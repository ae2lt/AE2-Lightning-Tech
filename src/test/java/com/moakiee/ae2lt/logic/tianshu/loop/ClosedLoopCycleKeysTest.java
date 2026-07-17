package com.moakiee.ae2lt.logic.tianshu.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ClosedLoopCycleKeysTest {
    @Test
    void stateConversionProtectsEveryStateInTheSeedScc() {
        var a = key("a");
        var b = key("b");

        var cycleKeys = ClosedLoopCycleKeys.analyze(List.of(
                pattern(List.of(stack(b, 1)), input(a, null)),
                pattern(List.of(stack(a, 2)), input(b, null))), List.of(a));

        assertEquals(java.util.Set.of(a, b), cycleKeys);
    }

    @Test
    void upstreamInputDoesNotJoinTheDependentSelfGrowthCycle() {
        var a = key("external_a");
        var b = key("seed_b");

        var cycleKeys = ClosedLoopCycleKeys.analyze(List.of(
                pattern(List.of(stack(a, 2)), input(a, null)),
                pattern(List.of(stack(b, 2)), input(b, null), input(a, null))), List.of(b));

        assertEquals(java.util.Set.of(b), cycleKeys);
    }

    @Test
    void returnedExternalCatalystDoesNotCreateAFalseReverseEdge() {
        var a = key("returned_a");
        var b = key("seed_b_with_catalyst");

        var cycleKeys = ClosedLoopCycleKeys.analyze(List.of(
                pattern(List.of(stack(b, 2)), input(b, null), input(a, a))), List.of(b));

        assertEquals(java.util.Set.of(b), cycleKeys);
    }

    @Test
    void groupedSeedFlowRemaindersSplitIntoBoundedPerExecutionVariants() {
        var input = key("split_input");
        var firstOutput = key("split_first_output");
        var firstThreeOutputs = key("split_first_three_outputs");
        var flow = new ClosedLoopPatternAnalyzer.MemberFlow(
                java.util.Map.of(input, 10L),
                java.util.Map.of(firstOutput, 1L, firstThreeOutputs, 3L));

        var slices = Ae2ClosedLoopPatternDetails.splitMemberFlow(flow, 5);

        assertEquals(3, slices.size());
        assertEquals(1L, slices.get(0).copiesPerCycle());
        assertEquals(java.util.Map.of(input, 2L), slices.get(0).inputSeed());
        assertEquals(java.util.Map.of(firstOutput, 1L, firstThreeOutputs, 1L),
                slices.get(0).outputSeed());
        assertEquals(2L, slices.get(1).copiesPerCycle());
        assertEquals(java.util.Map.of(firstThreeOutputs, 1L), slices.get(1).outputSeed());
        assertEquals(2L, slices.get(2).copiesPerCycle());
        assertEquals(java.util.Map.of(), slices.get(2).outputSeed());
    }

    @Test
    void consumerCreditRemaindersShareThePhysicalOutputCursor() {
        var a = key("credit_split_input");
        var b = key("credit_split_output");
        var first = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        var second = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002");
        var targets = new java.util.LinkedHashMap<java.util.UUID, java.util.Map<AEKey, Long>>();
        targets.put(first, java.util.Map.of(b, 1L));
        targets.put(second, java.util.Map.of(b, 1L));

        var slices = Ae2ClosedLoopPatternDetails.splitMemberFlow(
                new ClosedLoopPatternAnalyzer.MemberFlow(
                        java.util.Map.of(a, 2L), java.util.Map.of(b, 2L)),
                2L,
                targets);

        assertEquals(2, slices.size());
        assertEquals(1L, slices.get(0).copiesPerCycle());
        assertEquals(java.util.Map.of(first, java.util.Map.of(b, 1L)),
                slices.get(0).outputSeedCredits());
        assertEquals(1L, slices.get(1).copiesPerCycle());
        assertEquals(java.util.Map.of(second, java.util.Map.of(b, 1L)),
                slices.get(1).outputSeedCredits());
    }

    @Test
    void expandedSliceCountUsesRequestedFiringsAndPrimitiveCopiesOnly() {
        assertEquals(6L, Ae2ClosedLoopPatternDetails.expandedSliceCount(3, 2));
        assertEquals(com.moakiee.thunderbolt.core.planner.Sat.SAT,
                Ae2ClosedLoopPatternDetails.expandedSliceCount(
                        Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(0L, Ae2ClosedLoopPatternDetails.expandedSliceCount(1, 0));
    }

    @Test
    void sharedLedgerClassificationRequiresExactlyOneInputSeedTypePerMember() {
        var a = key("classification_a");
        var b = key("classification_b");

        assertTrue(ClosedLoopPatternAnalyzer.hasSingleSeedInputPerMember(List.of(
                new ClosedLoopPatternAnalyzer.MemberFlow(
                        java.util.Map.of(a, 1L), java.util.Map.of(b, 1L)),
                new ClosedLoopPatternAnalyzer.MemberFlow(
                        java.util.Map.of(b, 1L), java.util.Map.of(a, 1L)))));
        assertFalse(ClosedLoopPatternAnalyzer.hasSingleSeedInputPerMember(List.of(
                new ClosedLoopPatternAnalyzer.MemberFlow(
                        java.util.Map.of(), java.util.Map.of(a, 1L)))));
        assertFalse(ClosedLoopPatternAnalyzer.hasSingleSeedInputPerMember(List.of(
                new ClosedLoopPatternAnalyzer.MemberFlow(
                        java.util.Map.of(a, 1L, b, 1L), java.util.Map.of(a, 1L)))));
    }

    private static Pattern pattern(List<GenericStack> outputs, IPatternDetails.IInput... inputs) {
        return new Pattern(inputs, outputs);
    }

    private static Input input(AEKey key, AEKey remainder) {
        return new Input(new GenericStack[] {stack(key, 1)}, remainder);
    }

    private static GenericStack stack(AEKey key, long amount) {
        return new GenericStack(key, amount);
    }

    private static TestKey key(String id) {
        return new TestKey(id);
    }

    private record Pattern(IInput[] inputs, List<GenericStack> outputs) implements IPatternDetails {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return inputs; }
        @Override public List<GenericStack> getOutputs() { return outputs; }
    }

    private record Input(GenericStack[] possibleInputs, AEKey remainder) implements IPatternDetails.IInput {
        @Override public GenericStack[] getPossibleInputs() { return possibleInputs; }
        @Override public long getMultiplier() { return 1; }
        @Override public boolean isValid(AEKey input, Level level) {
            return possibleInputs[0].what().equals(input);
        }
        @Override public AEKey getRemainingKey(AEKey template) { return remainder; }
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;

        private TestKey(String id) { this.id = id; }
        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag(net.minecraft.core.HolderLookup.Provider registries) {
            var result = new CompoundTag();
            result.putString("id", id);
            return result;
        }
        @Override public Object getPrimaryKey() { return id; }
        @Override public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("ae2lt_test", id);
        }
        @Override public void writeToPacket(RegistryFriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() { return Component.literal(id); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        @Override public boolean hasComponents() { return false; }
        @Override public boolean equals(Object obj) {
            return obj instanceof TestKey other && id.equals(other.id);
        }
        @Override public int hashCode() { return id.hashCode(); }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(ResourceLocation.fromNamespaceAndPath("ae2lt_test", "cycle_key"),
                    TestKey.class, Component.literal("cycle key"));
        }
        @Override public MapCodec<? extends AEKey> codec() { return null; }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return null; }
    }
}
