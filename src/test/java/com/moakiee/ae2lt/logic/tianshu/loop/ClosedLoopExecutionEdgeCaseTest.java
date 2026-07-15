package com.moakiee.ae2lt.logic.tianshu.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.thunderbolt.ae2.api.crafting.ISeedPreservingCraftingTask;
import com.moakiee.thunderbolt.ae2.crafting.ExecuteLoopPattern;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadPatternDetails;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.ae2.overload.pattern.PatternExecutionHostKind;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelTaskPersistenceDefinition;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ClosedLoopExecutionEdgeCaseTest {
    @Test
    void fuzzySeedDebitUsesTheVariantActuallyExtractedFromTheCpu() {
        var plannedSeed = key("pickaxe", "planned_damage");
        var actualSeed = key("pickaxe", "actual_damage");
        var ordinaryInput = key("redstone", "");
        var delegate = new FakeLoopPattern(
                new IPatternDetails.IInput[] {
                        input(true, stack(plannedSeed, 1L)),
                        input(false, stack(ordinaryInput, 5L))
                },
                Set.of(0),
                UUID.randomUUID());
        var wrapped = new ExecuteLoopPattern(
                delegate, counter(plannedSeed, 1L), counter(plannedSeed, 1L));
        var holders = new KeyCounter[] {
                counter(actualSeed, 1L),
                counter(ordinaryInput, 5L)
        };

        assertTrue(wrapped.requiresActualSeedKeyTracking());
        assertTrue(wrapped.isInputSeedKey(actualSeed));
        assertFalse(wrapped.isInputSeedKey(ordinaryInput));
        var actualDebit = wrapped.actualInputSeed(holders);
        assertEquals(1L, actualDebit.get(actualSeed));
        assertEquals(0L, actualDebit.get(plannedSeed));
        assertEquals(0L, actualDebit.get(ordinaryInput));
    }

    @Test
    void exactSubstitutionSlotAlsoTracksTheConcreteAlternative() {
        var plannedSeed = key("iron_ingot", "");
        var alternativeSeed = key("copper_ingot", "");
        var delegate = new FakeLoopPattern(
                new IPatternDetails.IInput[] {
                        input(false, stack(plannedSeed, 1L), stack(alternativeSeed, 1L))
                },
                Set.of(),
                UUID.randomUUID());
        var wrapped = new ExecuteLoopPattern(
                delegate, counter(plannedSeed, 1L), counter(plannedSeed, 1L));

        assertTrue(wrapped.requiresActualSeedKeyTracking());
        assertTrue(wrapped.isInputSeedKey(alternativeSeed));
        assertEquals(1L,
                wrapped.actualInputSeed(new KeyCounter[] {counter(alternativeSeed, 1L)})
                        .get(alternativeSeed));
    }

    @Test
    void fuzzySelfReturningInputPreservesTheRuntimeVariantAsItsRemainder() {
        var encodedVariant = key("pickaxe", "encoded_damage");
        var plannedSeed = key("pickaxe", "planned_damage");
        var runtimeVariant = key("pickaxe", "runtime_damage");
        var unrelated = key("diamond_pickaxe", "");
        var delegate = new FakeLoopPattern(
                new IPatternDetails.IInput[] {input(true, stack(encodedVariant, 1L))},
                Set.of(0),
                UUID.randomUUID());

        var pinned = ClosedLoopExpandedPatternDetails.pinReusableSeedInputs(
                delegate, Set.of(plannedSeed))[0];

        assertEquals(plannedSeed, pinned.getPossibleInputs()[0].what());
        assertTrue(pinned.isValid(runtimeVariant, null));
        assertFalse(pinned.isValid(unrelated, null));
        assertEquals(runtimeVariant, pinned.getRemainingKey(runtimeVariant));
        assertNull(pinned.getRemainingKey(unrelated));
    }

    private static FakeInput input(boolean selfReturning, GenericStack... possible) {
        return new FakeInput(possible, selfReturning);
    }

    private static GenericStack stack(AEKey key, long amount) {
        return new GenericStack(key, amount);
    }

    private static KeyCounter counter(AEKey key, long amount) {
        var result = new KeyCounter();
        result.add(key, amount);
        return result;
    }

    private static TestKey key(String id, String secondary) {
        return new TestKey(id, secondary);
    }

    private record FakeInput(GenericStack[] possible, boolean selfReturning)
            implements IPatternDetails.IInput {
        @Override public GenericStack[] getPossibleInputs() { return possible.clone(); }
        @Override public long getMultiplier() { return 1L; }
        @Override public boolean isValid(AEKey input, Level level) {
            return java.util.Arrays.stream(possible).anyMatch(stack -> stack.what().equals(input));
        }
        @Override public @Nullable AEKey getRemainingKey(AEKey template) {
            return selfReturning ? template : null;
        }
    }

    private record FakeLoopPattern(
            IPatternDetails.IInput[] inputs,
            Set<Integer> fuzzySlots,
            UUID groupId)
            implements IPatternDetails, OverloadedProviderOnlyPatternDetails,
            ISeedPreservingCraftingTask, TimeWheelTaskPersistenceDefinition {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IPatternDetails.IInput[] getInputs() { return inputs.clone(); }
        @Override public List<GenericStack> getOutputs() { return List.of(); }
        @Override public PatternExecutionHostKind requiredHostKind() {
            return PatternExecutionHostKind.OVERLOADED_PATTERN_PROVIDER;
        }
        @Override public String overloadPatternIdentity() { return "test:fuzzy-loop"; }
        @Override public OverloadPatternDetails overloadPatternDetailsView() { return null; }
        @Override public boolean hasFuzzyInputs() { return !fuzzySlots.isEmpty(); }
        @Override public boolean isFuzzyInput(int slot) { return fuzzySlots.contains(slot); }
        @Override public UUID reusableSeedGroupId() { return groupId; }
        @Override public Set<AEKey> reusableSeedCycleKeys() { return Set.of(); }
        @Override public boolean hasSingleSeedInputPerMember() { return true; }
        @Override public AEItemKey timeWheelPersistenceDefinition() { return null; }
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;
        private final String secondary;

        private TestKey(String id, String secondary) {
            this.id = id;
            this.secondary = secondary;
        }

        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() {
            return secondary.isEmpty() ? this : new TestKey(id, "");
        }
        @Override public CompoundTag toTag(net.minecraft.core.HolderLookup.Provider registries) {
            var tag = new CompoundTag();
            tag.putString("id", id);
            tag.putString("secondary", secondary);
            return tag;
        }
        @Override public Object getPrimaryKey() { return id; }
        @Override public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("ae2lt_test", id);
        }
        @Override public void writeToPacket(RegistryFriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() {
            return Component.literal(id + secondary);
        }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        @Override public boolean hasComponents() { return !secondary.isEmpty(); }
        @Override public boolean equals(Object obj) {
            return obj instanceof TestKey other
                    && id.equals(other.id)
                    && secondary.equals(other.secondary);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, secondary); }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(ResourceLocation.fromNamespaceAndPath("ae2lt_test", "execution_key"),
                    TestKey.class, Component.literal("execution key"));
        }
        @Override public MapCodec<? extends AEKey> codec() { return null; }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return null; }
    }
}
