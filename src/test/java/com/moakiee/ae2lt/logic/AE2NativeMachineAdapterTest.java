package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderTarget;

class AE2NativeMachineAdapterTest {
    private static final TestKey STICK = new TestKey("stick");
    private static final TestKey COBBLESTONE = new TestKey("cobblestone");

    @Test
    void vanillaSingleCopyDispatchesTheAcceptedPrefixAndOwnsTheRemainder() {
        var target = new CapacityTarget(Map.of(STICK, 16L));

        var result = AE2NativeMachineAdapter.pushPlannedInputs(
                target,
                List.of(new GenericStack(STICK, 64L)),
                1,
                PatternInputAcceptance.VANILLA_SINGLE_COPY);

        assertEquals(1, result.acceptedCopies());
        assertEquals(16L, target.inserted(STICK));
        assertEquals(List.of(new GenericStack(STICK, 48L)), result.overflow());
    }

    @Test
    void vanillaSingleCopyStillPushesLaterInputsAfterAnEarlierPartialInsert() {
        var target = new CapacityTarget(Map.of(
                STICK, 16L,
                COBBLESTONE, 64L));

        var result = AE2NativeMachineAdapter.pushPlannedInputs(
                target,
                List.of(
                        new GenericStack(STICK, 64L),
                        new GenericStack(COBBLESTONE, 64L)),
                1,
                PatternInputAcceptance.VANILLA_SINGLE_COPY);

        assertEquals(1, result.acceptedCopies());
        assertEquals(16L, target.inserted(STICK));
        assertEquals(64L, target.inserted(COBBLESTONE));
        assertEquals(List.of(new GenericStack(STICK, 48L)), result.overflow());
    }

    @Test
    void vanillaSingleCopyRejectsBeforeMutationWhenAnyInputAcceptsNothing() {
        var target = new CapacityTarget(Map.of(
                STICK, 16L,
                COBBLESTONE, 0L));

        var result = AE2NativeMachineAdapter.pushPlannedInputs(
                target,
                List.of(
                        new GenericStack(STICK, 64L),
                        new GenericStack(COBBLESTONE, 64L)),
                1,
                PatternInputAcceptance.VANILLA_SINGLE_COPY);

        assertEquals(PushResult.REJECTED, result);
        assertEquals(0L, target.inserted(STICK));
        assertEquals(0L, target.inserted(COBBLESTONE));
    }

    @Test
    void adaptiveBatchStillRejectsAnIncompleteAggregateBeforeMutation() {
        var target = new CapacityTarget(Map.of(STICK, 16L));

        var result = AE2NativeMachineAdapter.pushPlannedInputs(
                target,
                List.of(new GenericStack(STICK, 64L)),
                4,
                PatternInputAcceptance.COMPLETE_BATCH);

        assertEquals(PushResult.REJECTED, result);
        assertEquals(0L, target.inserted(STICK));
    }

    private static final class CapacityTarget implements PatternProviderTarget {
        private final Map<AEKey, Long> remaining;
        private final Map<AEKey, Long> inserted = new HashMap<>();

        private CapacityTarget(Map<? extends AEKey, Long> capacities) {
            this.remaining = new HashMap<>(capacities);
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode) {
            long accepted = Math.min(
                    Math.max(0L, remaining.getOrDefault(what, 0L)),
                    Math.max(0L, amount));
            if (mode == Actionable.MODULATE && accepted > 0L) {
                remaining.merge(what, -accepted, Long::sum);
                inserted.merge(what, accepted, Long::sum);
            }
            return accepted;
        }

        @Override
        public boolean containsPatternInput(Set<AEKey> patternInputs) {
            return false;
        }

        private long inserted(AEKey what) {
            return inserted.getOrDefault(what, 0L);
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
            super(
                    ResourceLocation.fromNamespaceAndPath("ae2lt_test", "key"),
                    TestKey.class,
                    Component.literal("test key"));
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
