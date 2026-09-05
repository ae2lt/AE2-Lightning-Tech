package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.mojang.serialization.MapCodec;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;

/** Regression coverage for the production persistent-buffer and retry paths. */
class OverloadedInterfaceBufferPathTest {
    private static final TestKeyType ITEMS = new TestKeyType("items");
    private static final TestKeyType FLUIDS = new TestKeyType("fluids");

    @Test
    void rejectedPrefixDoesNotLockAnUntouchedTailOfTheSameKeyType() {
        var buffer = new LinkedHashMap<AEKey, Long>();
        TestKey tail = null;
        var rejected = new HashSet<AEKey>();
        for (int i = 0; i < 16_385; i++) {
            var key = new TestKey(ITEMS, "item-" + i);
            buffer.put(key, 1L);
            if (i < 16_384) {
                rejected.add(key);
            } else {
                tail = key;
            }
        }

        var locks = new IdentityHashMap<AEKeyType, Long>();
        var storage = new ProgrammableStorage(key -> rejected.contains(key));
        var first = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, Long.MIN_VALUE, false, 0,
                storage, IActionSource.empty(), 0, () -> {});

        assertEquals(16_384, storage.attempts);
        assertFalse(locks.containsKey(ITEMS),
                "a bounded prefix cannot prove that the whole type is rejected");
        assertEquals(16_385, buffer.size());

        var second = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, first.lastFlushTick(), first.flushLimited(),
                first.remainingKeys(), storage, IActionSource.empty(), 5, () -> {});

        assertTrue(storage.inserted.getOrDefault(tail, 0L) > 0,
                "the accepted tail key must be reached after the prefix rotates");
        assertNull(buffer.get(tail));
        assertFalse(locks.containsKey(ITEMS));
        assertEquals(16_384, buffer.size());
        assertEquals(1L, storage.totalInserted(), "every accepted key is accounted once");
        assertEquals(16_384L, buffer.values().stream().mapToLong(Long::longValue).sum());
        assertEquals(second.lastFlushTick(), 5L);
    }

    @Test
    void partialInsertAndMixedTypesKeepPerKeyOwnershipAndOnlyLockFullyTestedTypes() {
        var aRejected = new TestKey(ITEMS, "reject");
        var aPartial = new TestKey(ITEMS, "partial");
        var bAccepted = new TestKey(FLUIDS, "accepted");
        var buffer = new LinkedHashMap<AEKey, Long>();
        buffer.put(aRejected, 4L);
        buffer.put(aPartial, 10L);
        buffer.put(bAccepted, 7L);

        var capacities = Map.of(aPartial, 2L, bAccepted, 7L);
        var storage = new ProgrammableStorage(key -> key.equals(aRejected), capacities);
        var locks = new IdentityHashMap<AEKeyType, Long>();
        var result = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, Long.MIN_VALUE, false, 0,
                storage, IActionSource.empty(), 0, () -> {});

        assertEquals(4L, buffer.get(aRejected));
        assertEquals(8L, buffer.get(aPartial));
        assertNull(buffer.get(bAccepted));
        assertFalse(locks.containsKey(ITEMS),
                "a partial insert proves that this type is not fully rejected");
        assertFalse(locks.containsKey(FLUIDS));
        assertEquals(9L, storage.totalInserted());
        assertTrue(result.changed());
    }

    @Test
    void stoppedHighCardinalityBacklogDrainsWithoutDroppingKeys() {
        int keyCount = 49_152;
        var buffer = new LinkedHashMap<AEKey, Long>(keyCount);
        for (int i = 0; i < keyCount; i++) {
            buffer.put(new TestKey(ITEMS, "drain-" + i), 1L);
        }

        var storage = new ProgrammableStorage(ignored -> false);
        var locks = new IdentityHashMap<AEKeyType, Long>();
        var first = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, Long.MIN_VALUE, false, 0,
                storage, IActionSource.empty(), 0, () -> {});

        assertEquals(16_384, storage.attempts,
                "the initial flush must honor the configured first slice");
        assertEquals(32_768, buffer.size());
        assertTrue(first.flushLimited());

        int attemptsAfterFirst = storage.attempts;
        var second = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, first.lastFlushTick(), first.flushLimited(),
                first.remainingKeys(), storage, IActionSource.empty(), 1, () -> {});

        assertEquals(32_768, storage.attempts - attemptsAfterFirst,
                "the current stopped-backlog path expands the next drain slice; "
                        + "keep this cost visible when evaluating a bounded replacement");
        assertTrue(buffer.isEmpty());
        assertFalse(second.flushLimited());
        assertEquals(keyCount, storage.totalInserted());
        assertTrue(locks.isEmpty());
    }

    @Test
    void aRejectedTypeRecoversAndItsLockIsRemovedAfterARealSuccessfulFlush() {
        var key = new TestKey(ITEMS, "recovery");
        var buffer = new LinkedHashMap<AEKey, Long>();
        buffer.put(key, 6L);
        var locks = new IdentityHashMap<AEKeyType, Long>();
        var unavailable = new ProgrammableStorage(ignored -> true);

        var first = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, Long.MIN_VALUE, false, 0,
                unavailable, IActionSource.empty(), 0, () -> {});
        assertEquals(20L, locks.get(ITEMS));
        assertEquals(6L, buffer.get(key));

        unavailable.rejection = ignored -> false;
        var second = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, first.lastFlushTick(), first.flushLimited(),
                first.remainingKeys(), unavailable, IActionSource.empty(), 5, () -> {});

        assertTrue(second.changed());
        assertFalse(locks.containsKey(ITEMS));
        assertTrue(buffer.isEmpty());
        assertEquals(6L, unavailable.totalInserted());
    }

    @Test
    void fastExportRetryIsNeverSelectedForNormalMode() {
        int threshold = OverloadedInterfaceBlockEntity.EXPORT_REJECT_FAST_RETRY_THRESHOLD;
        assertFalse(OverloadedInterfaceBlockEntity.shouldUseFastExportRejectRetry(
                OverloadedInterfaceBlockEntity.IOSpeedMode.NORMAL, threshold));
        assertFalse(OverloadedInterfaceBlockEntity.shouldUseFastExportRejectRetry(
                OverloadedInterfaceBlockEntity.IOSpeedMode.NORMAL, threshold + 10));
        assertFalse(OverloadedInterfaceBlockEntity.shouldUseFastExportRejectRetry(
                OverloadedInterfaceBlockEntity.IOSpeedMode.FAST, threshold - 1));
        assertTrue(OverloadedInterfaceBlockEntity.shouldUseFastExportRejectRetry(
                OverloadedInterfaceBlockEntity.IOSpeedMode.FAST, threshold));
    }

    private static final class ProgrammableStorage implements MEStorage {
        private Predicate<AEKey> rejection;
        private final Map<AEKey, Long> capacity;
        private final Map<AEKey, Long> inserted = new HashMap<>();
        private int attempts;

        private ProgrammableStorage(Predicate<AEKey> rejection) {
            this(rejection, Map.of());
        }

        private ProgrammableStorage(
                Predicate<AEKey> rejection, Map<? extends AEKey, Long> capacity) {
            this.rejection = rejection;
            this.capacity = new HashMap<>(capacity);
        }

        @Override
        public Component getDescription() {
            return Component.literal("programmable test storage");
        }

        @Override
        public long insert(
                AEKey key, long amount, Actionable mode, IActionSource source) {
            attempts++;
            if (rejection.test(key) || amount <= 0) return 0;
            long accepted = Math.min(amount, capacity.getOrDefault(key, Long.MAX_VALUE));
            if (mode == Actionable.MODULATE && accepted > 0) {
                inserted.merge(key, accepted, Long::sum);
                capacity.merge(key, -accepted, Long::sum);
            }
            return accepted;
        }

        private long totalInserted() {
            return inserted.values().stream().mapToLong(Long::longValue).sum();
        }
    }

    private static final class TestKey extends AEKey {
        private final AEKeyType type;
        private final String id;

        private TestKey(AEKeyType type, String id) {
            this.type = type;
            this.id = id;
        }

        @Override
        public AEKeyType getType() {
            return type;
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
            return obj instanceof TestKey other
                    && type == other.type
                    && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(type) + id.hashCode();
        }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType(String id) {
            super(
                    ResourceLocation.fromNamespaceAndPath("ae2lt_test", id),
                    TestKey.class,
                    Component.literal(id));
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
