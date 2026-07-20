package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class TianshuPatternTerminalStorageTest {
    private static final IActionSource SOURCE = IActionSource.empty();

    @Test
    void warehouseEntriesAppearOnlyWhileTheBoundCpuIsAvailable() {
        var formed = new boolean[] { false };
        var pattern = new TestKey("closed_loop_pattern");
        var warehouse = new ArrayList<AEKey>(List.of(pattern));
        var storage = new TianshuPatternTerminalStorage(
                () -> null,
                () -> formed[0] ? List.copyOf(warehouse) : List.of(),
                warehouse::remove);

        assertEquals(0L, available(storage, pattern));
        assertEquals(0L, storage.extract(pattern, 1, Actionable.SIMULATE, SOURCE));

        formed[0] = true;
        assertEquals(1L, available(storage, pattern));
        assertEquals(1L, storage.extract(pattern, 1, Actionable.SIMULATE, SOURCE));
        assertEquals(1, warehouse.size(), "simulation must not remove the physical pattern");

        formed[0] = false;
        assertEquals(0L, available(storage, pattern));
        assertEquals(0L, storage.extract(pattern, 1, Actionable.MODULATE, SOURCE));
        assertEquals(1, warehouse.size());

        formed[0] = true;
        assertEquals(1L, storage.extract(pattern, 1, Actionable.MODULATE, SOURCE));
        assertEquals(0, warehouse.size());
    }

    @Test
    void normalNetworkStorageIsMergedAndRemainsTheOnlyInsertionTarget() {
        var pattern = new TestKey("shared_pattern");
        var other = new TestKey("ordinary_item");
        var network = new TestStorage();
        network.amounts.put(pattern, 2L);
        var warehouse = new ArrayList<AEKey>(List.of(pattern));
        var storage = new TianshuPatternTerminalStorage(
                () -> network,
                () -> List.copyOf(warehouse),
                warehouse::remove);

        assertEquals(3L, available(storage, pattern));
        assertEquals(3L, storage.extract(pattern, 3, Actionable.MODULATE, SOURCE));
        assertEquals(0L, network.amounts.getOrDefault(pattern, 0L));
        assertEquals(0, warehouse.size());

        assertEquals(4L, storage.insert(other, 4, Actionable.MODULATE, SOURCE));
        assertEquals(4L, network.amounts.get(other));
        assertEquals(0, warehouse.size(), "ME insertion must not bypass the explicit upload action");
    }

    private static long available(MEStorage storage, AEKey key) {
        var counter = new KeyCounter();
        storage.getAvailableStacks(counter);
        return counter.get(key);
    }

    private static final class TestStorage implements MEStorage {
        private final Map<AEKey, Long> amounts = new HashMap<>();

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) amounts.merge(what, amount, Long::sum);
            return amount;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(amounts.getOrDefault(what, 0L), amount);
            if (mode == Actionable.MODULATE && extracted > 0L) {
                amounts.put(what, amounts.get(what) - extracted);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            amounts.forEach(out::add);
        }

        @Override
        public Component getDescription() {
            return Component.literal("test storage");
        }
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;

        private TestKey(String id) {
            this.id = id;
        }

        @Override
        public AEKeyType getType() {
            return TYPE;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag(net.minecraft.core.HolderLookup.Provider registries) {
            var tag = new CompoundTag();
            tag.putString("id", id);
            return tag;
        }

        @Override
        public Object getPrimaryKey() {
            return id;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("ae2lt_test", id);
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf data) {
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id);
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        }

        @Override
        public boolean hasComponents() {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TestKey other && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(ResourceLocation.fromNamespaceAndPath("ae2lt_test", "pattern_terminal_storage"),
                    TestKey.class, Component.literal("test key"));
        }

        @Override
        public MapCodec<? extends AEKey> codec() {
            return null;
        }

        @Override
        public AEKey readFromPacket(RegistryFriendlyByteBuf input) {
            return null;
        }
    }
}
