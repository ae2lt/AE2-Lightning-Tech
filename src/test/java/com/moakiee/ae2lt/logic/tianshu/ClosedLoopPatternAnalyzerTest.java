package com.moakiee.ae2lt.logic.tianshu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternAnalyzer;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternAuthoringService;
import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadPatternDetails;
import com.moakiee.thunderbolt.ae2.overload.pattern.PatternExecutionHostKind;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;

class ClosedLoopPatternAnalyzerTest {
    @Test
    void reusableCatalystBecomesOneSeedInsteadOfScaledInput() {
        var crystal = key("infusion_crystal");
        var essence = key("essence");
        var upgraded = key("upgraded_essence");
        var pattern = pattern(
                List.of(output(upgraded, 1)),
                input(crystal, 1, crystal), input(essence, 4, null));

        var analysis = ClosedLoopPatternAnalyzer.analyze(
                List.of(new ClosedLoopPatternAnalyzer.Member(pattern, 1)), upgraded);

        assertNotNull(analysis);
        assertStack(analysis.seeds(), crystal, 1);
        assertStack(analysis.externalInputs(), essence, 4);
        assertStack(analysis.netOutputs(), upgraded, 1);
    }

    @Test
    void smithingTemplateDuplicationUsesOneTemplateAsSeed() {
        var template = key("smithing_template");
        var diamond = key("diamond");
        var base = key("netherrack");
        var pattern = pattern(
                List.of(output(template, 2)),
                input(template, 1, null), input(diamond, 7, null), input(base, 1, null));

        var analysis = ClosedLoopPatternAnalyzer.analyze(
                List.of(new ClosedLoopPatternAnalyzer.Member(pattern, 1)), template);

        assertNotNull(analysis);
        assertStack(analysis.seeds(), template, 1);
        assertEquals(2, analysis.externalInputs().size());
        assertAmount(analysis.externalInputs(), diamond, 7);
        assertAmount(analysis.externalInputs(), base, 1);
        assertStack(analysis.netOutputs(), template, 1);
    }

    @Test
    void memberCopiesAreOneDispatchGroupAndCannotRecycleOutputsInsideTheGroup() {
        var seed = key("grouped_seed");
        var duplicate = pattern(List.of(output(seed, 2)), input(seed, 1, null));

        var analysis = ClosedLoopPatternAnalyzer.analyze(
                List.of(new ClosedLoopPatternAnalyzer.Member(duplicate, 1_000)), seed);

        assertNotNull(analysis);
        assertStack(analysis.seeds(), seed, 1_000);
        assertEquals(List.of(), analysis.externalInputs());
        assertStack(analysis.netOutputs(), seed, 1_000);
    }

    @Test
    void reactionChamberLoopContractsToNetCertusOutput() {
        var charged = key("charged_certus_quartz_crystal");
        var dust = key("certus_quartz_dust");
        var certus = key("certus_quartz_crystal");
        var reaction = pattern(
                List.of(output(certus, 64)),
                input(charged, 16, null), input(dust, 16, null));
        var chargeAndGrind = pattern(
                List.of(output(charged, 16), output(dust, 16)),
                input(certus, 32, null));

        var analysis = ClosedLoopPatternAnalyzer.analyze(List.of(
                new ClosedLoopPatternAnalyzer.Member(reaction, 1),
                new ClosedLoopPatternAnalyzer.Member(chargeAndGrind, 1)), certus);

        assertNotNull(analysis);
        assertEquals(2, analysis.seeds().size());
        assertAmount(analysis.seeds(), charged, 16);
        assertAmount(analysis.seeds(), dust, 16);
        assertEquals(List.of(), analysis.externalInputs());
        assertStack(analysis.netOutputs(), certus, 32);
    }

    @Test
    void rejectsAmbiguousInputsNonCyclesAndNetNegativeCycleMaterials() {
        var a = key("a");
        var b = key("b");
        var c = key("c");
        var ambiguous = new FakeInput(new GenericStack[] {output(a, 1), output(b, 1)}, null);
        assertNull(ClosedLoopPatternAnalyzer.analyze(
                List.of(new ClosedLoopPatternAnalyzer.Member(pattern(List.of(output(c, 1)), ambiguous), 1)), c));
        assertNull(ClosedLoopPatternAnalyzer.analyze(List.of(new ClosedLoopPatternAnalyzer.Member(
                pattern(List.of(output(c, 1)), input(a, 1, null)), 1)), c));
        assertNull(ClosedLoopPatternAnalyzer.analyze(List.of(new ClosedLoopPatternAnalyzer.Member(
                pattern(List.of(output(a, 1), output(c, 1)), input(a, 2, null)), 1)), c));
    }

    @Test
    void hugeCopyCountIsSaturatedAndDoesNotIteratePerCopy() {
        var catalyst = key("catalyst");
        var product = key("product");
        var member = new ClosedLoopPatternAnalyzer.Member(pattern(
                List.of(output(catalyst, 1), output(product, Long.MAX_VALUE)),
                input(catalyst, 1, null)), Long.MAX_VALUE);

        var analysis = assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> ClosedLoopPatternAnalyzer.analyze(List.of(member), product));

        assertNotNull(analysis);
        // One member group cannot recycle its own outputs, so its whole saturated copy count is
        // also the simultaneous catalyst seed requirement.
        assertAmount(analysis.seeds(), catalyst, Long.MAX_VALUE / 4);
        // AE stack arithmetic intentionally clamps below Long.MAX_VALUE.
        assertAmount(analysis.netOutputs(), product, Long.MAX_VALUE / 4);
    }

    @Test
    void repeatedDecliningMemberConsumesBalanceBeforeRequestingMoreSeed() {
        var catalyst = key("buffered_catalyst");
        var product = key("product");
        var charge = pattern(List.of(output(catalyst, 10)), input(catalyst, 1, null));
        var spend = pattern(List.of(output(catalyst, 1), output(product, 1)), input(catalyst, 2, null));

        var analysis = ClosedLoopPatternAnalyzer.analyze(List.of(
                new ClosedLoopPatternAnalyzer.Member(charge, 1),
                new ClosedLoopPatternAnalyzer.Member(spend, 5)), product);

        assertNotNull(analysis);
        assertAmount(analysis.seeds(), catalyst, 1);
        assertAmount(analysis.netOutputs(), catalyst, 4);
        assertAmount(analysis.netOutputs(), product, 5);
    }

    @Test
    void idOnlyOverloadMemberClosesWhenItsConcreteOutputCanFeedTheFuzzyInput() {
        var inputSeed = new TestKey("overload_seed", "input_nbt");
        var returnedSeed = new TestKey("overload_seed", "output_nbt");
        var material = key("overload_material");
        var product = key("overload_product");
        var base = pattern(List.of(output(returnedSeed, 1), output(product, 1)),
                input(inputSeed, 1, null), input(material, 1, null));
        var strict = new FakeOverloadPattern(base.inputs(), base.outputs(), false, false);
        var idOnly = new FakeOverloadPattern(base.inputs(), base.outputs(), true, false);

        assertNull(ClosedLoopPatternAnalyzer.analyze(
                List.of(new ClosedLoopPatternAnalyzer.Member(strict, 1)), product));
        var analysis = ClosedLoopPatternAnalyzer.analyze(
                List.of(new ClosedLoopPatternAnalyzer.Member(idOnly, 1)), product);
        assertNotNull(analysis);
        assertAmount(analysis.seeds(), returnedSeed, 1);
        assertAmount(analysis.externalInputs(), material, 1);
    }

    @Test
    void overloadLoopEdgesFollowStrictAndIdOnlyContainment() {
        var exact = new TestKey("contained_seed", "exact");
        var other = new TestKey("contained_seed", "other");
        var product = key("contained_product");

        assertNotNull(analyzeOverloadEdge(exact, exact, product, false, false),
                "STRICT output must enter matching STRICT input");
        assertNotNull(analyzeOverloadEdge(exact, other, product, true, false),
                "STRICT output must enter ID_ONLY input");
        assertNotNull(analyzeOverloadEdge(exact, other, product, true, true),
                "ID_ONLY output must enter ID_ONLY input");
        assertNull(analyzeOverloadEdge(exact, exact, product, false, true),
                "ID_ONLY output cannot guarantee a STRICT input");
    }

    @Test
    void exactBalancerFindsSixMemberSolutionBeyondTheOldProbeBudget() {
        var ratioA = key("ratio_a");
        var ratioB = key("ratio_b");
        var target = key("six_member_target");
        var lower = java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> key("lower_" + i)).toList();
        var upper = java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> key("upper_" + i)).toList();

        var rootInputs = new java.util.ArrayList<IPatternDetails.IInput>();
        rootInputs.add(input(ratioA, 3, null));
        for (var key : lower) rootInputs.add(input(key, 4, null));
        var rootOutputs = new java.util.ArrayList<GenericStack>();
        rootOutputs.add(output(ratioB, 3));
        rootOutputs.add(output(target, 1));
        for (var key : upper) rootOutputs.add(output(key, 4));

        var members = new java.util.ArrayList<IPatternDetails>();
        members.add(pattern(rootOutputs,
                rootInputs.toArray(IPatternDetails.IInput[]::new)));
        members.add(pattern(List.of(output(ratioA, 2)), input(ratioB, 2, null)));
        for (int i = 0; i < 4; i++) {
            members.add(pattern(List.of(output(lower.get(i), 1)),
                    input(upper.get(i), 1, null)));
        }

        var solved = ClosedLoopPatternAnalyzer.solveCoefficients(members, target);

        assertEquals(com.moakiee.thunderbolt.core.planner.PositiveIntegerLinearSolver.Status.SOLVED,
                solved.status());
        assertArrayEquals(new long[] {2, 3, 8, 8, 8, 8}, solved.coefficients());
        var analyzed = new java.util.ArrayList<ClosedLoopPatternAnalyzer.Member>();
        for (int i = 0; i < members.size(); i++) {
            analyzed.add(new ClosedLoopPatternAnalyzer.Member(
                    members.get(i), solved.coefficients()[i]));
        }
        assertNotNull(ClosedLoopPatternAnalyzer.analyze(analyzed, target));
    }

    @Test
    void downstreamConsumerIsRejectedByTheMinimalClosedLoopRule() {
        var seed = key("minimal_seed");
        var product = key("downstream_product");
        var grow = pattern(List.of(output(seed, 2)), input(seed, 1, null));
        var downstream = pattern(List.of(output(product, 1)), input(seed, 2, null));

        var members = List.<IPatternDetails>of(grow, downstream);
        var solved = ClosedLoopPatternAnalyzer.solveCoefficients(members, product);

        assertEquals(com.moakiee.thunderbolt.core.planner.PositiveIntegerLinearSolver.Status.SOLVED,
                solved.status(), "mass balance alone is feasible and must not hide the structure bug");
        assertEquals(ClosedLoopPatternAnalyzer.StructureStatus.NOT_CONNECTED,
                ClosedLoopPatternAnalyzer.validateMinimalStructure(members));
    }

    @Test
    void connectedCompositeIsRejectedWhenOneMemberAlreadyFormsItsOwnLoop() {
        var seed = key("non_minimal_seed");
        var product = key("non_minimal_product");
        var selfReplicating = pattern(List.of(output(seed, 2)), input(seed, 1, null));
        var returnedSeedProduct = pattern(
                List.of(output(seed, 1), output(product, 1)), input(seed, 1, null));

        assertEquals(ClosedLoopPatternAnalyzer.StructureStatus.NOT_MINIMAL,
                ClosedLoopPatternAnalyzer.validateMinimalStructure(
                        List.of(selfReplicating, returnedSeedProduct)));
    }

    @Test
    void balancedTwoMemberCycleIsConnectedMinimalAndRequiresASeed() {
        var a = key("minimal_cycle_a");
        var b = key("minimal_cycle_b");
        var product = key("minimal_cycle_product");
        var first = pattern(List.of(output(b, 1)), input(a, 1, null));
        var second = pattern(List.of(output(a, 1), output(product, 1)), input(b, 1, null));
        var members = List.<IPatternDetails>of(first, second);

        assertEquals(ClosedLoopPatternAnalyzer.StructureStatus.VALID,
                ClosedLoopPatternAnalyzer.validateMinimalStructure(members));
        var solved = ClosedLoopPatternAnalyzer.solveCoefficients(members, product);
        assertArrayEquals(new long[] {1, 1}, solved.coefficients());
        var analysis = ClosedLoopPatternAnalyzer.analyze(List.of(
                new ClosedLoopPatternAnalyzer.Member(first, 1),
                new ClosedLoopPatternAnalyzer.Member(second, 1)), product);
        assertNotNull(analysis);
        assertAmount(analysis.seeds(), a, 1);
    }

    @Test
    void automaticOrderingMinimizesTheSimultaneousSeedPool() {
        var a = key("ordered_a");
        var b = key("ordered_b");
        var product = key("ordered_product");
        var expensiveFirst = pattern(List.of(output(b, 10)), input(a, 10, null));
        var cheapFirst = pattern(
                List.of(output(a, 10), output(product, 1)), input(b, 1, null));

        var ordered = ClosedLoopPatternAnalyzer.analyzeBestOrder(List.of(
                new ClosedLoopPatternAnalyzer.Member(expensiveFirst, 1),
                new ClosedLoopPatternAnalyzer.Member(cheapFirst, 1)), product);

        assertNotNull(ordered);
        assertEquals(cheapFirst, ordered.members().getFirst().details());
        assertStack(ordered.analysis().seeds(), b, 1);
    }

    @Test
    void markedAuthoringDerivesSeedsConsumablesByproductsAndNetOutputs() {
        var seed = key("marked_seed");
        var material = key("marked_material");
        var product = key("marked_product");
        var byproduct = key("marked_byproduct");
        var member = pattern(
                List.of(output(seed, 2), output(product, 1), output(byproduct, 3)),
                input(seed, 1, null), input(material, 4, null));
        var snapshot = new SourcePatternSnapshot(
                ResourceLocation.fromNamespaceAndPath("ae2lt_test", "marked_pattern"), null, null);

        var result = ClosedLoopPatternAuthoringService.create(List.of(
                new ClosedLoopPatternAuthoringService.MarkedMember(member, snapshot, 1_000)),
                product, 8);

        assertEquals(ClosedLoopPatternAuthoringService.Status.VALID, result.status());
        var payload = result.payload();
        assertNotNull(payload);
        assertEquals(8, payload.seedMultiplier());
        assertStack(payload.seeds(), seed, 1_000);
        assertStack(payload.externalInputs(), material, 4_000);
        assertAmount(payload.netOutputs(), seed, 1_000);
        assertAmount(payload.netOutputs(), product, 1_000);
        assertAmount(payload.netOutputs(), byproduct, 3_000);
    }

    @Test
    void productionWithoutAnyReusableSeedIsNotAClosedLoop() {
        var product = key("seedless_product");
        var seedless = pattern(List.of(output(product, 1)));

        var solved = ClosedLoopPatternAnalyzer.solveCoefficients(List.of(seedless), product);

        assertEquals(com.moakiee.thunderbolt.core.planner.PositiveIntegerLinearSolver.Status.INFEASIBLE,
                solved.status());
        assertEquals(ClosedLoopPatternAnalyzer.StructureStatus.NOT_CONNECTED,
                ClosedLoopPatternAnalyzer.validateMinimalStructure(List.of(seedless)));
    }

    @Test
    void fuzzySeedSlotKeepsOnePlanningIdentityButAcceptsTheReturnedRuntimeVariant() {
        var encodedInput = new TestKey("pinned_seed", "encoded");
        var returnedVariant = new TestKey("pinned_seed", "returned");
        var otherReturnedVariant = new TestKey("pinned_seed", "other");
        var details = new FakeOverloadPattern(
                new IPatternDetails.IInput[] {input(encodedInput, 1, null)},
                List.of(output(returnedVariant, 1)), true, false);

        var pinned = com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopExpandedPatternDetails
                .pinReusableSeedInputs(details, java.util.Set.of(returnedVariant));

        assertEquals(returnedVariant, pinned[0].getPossibleInputs()[0].what());
        assertEquals(true, pinned[0].isValid(returnedVariant, null));
        assertEquals(true, pinned[0].isValid(encodedInput, null));
        assertThrows(IllegalArgumentException.class,
                () -> com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopExpandedPatternDetails
                        .pinReusableSeedInputs(details,
                                java.util.Set.of(returnedVariant, otherReturnedVariant)));
    }

    @Test
    void memberFlowsCarrySeedStateAcrossDifferentKeysAndExposeOnlySafeOutput() {
        var a = key("flow_a");
        var b = key("flow_b");
        var aToB = pattern(List.of(output(b, 1)), input(a, 1, null));
        var bToA = pattern(List.of(output(a, 2)), input(b, 1, null));

        var flows = ClosedLoopPatternAnalyzer.deriveMemberFlows(List.of(
                new ClosedLoopPatternAnalyzer.Member(aToB, 1),
                new ClosedLoopPatternAnalyzer.Member(bToA, 1)),
                List.of(output(a, 1)));

        assertEquals(java.util.Map.of(a, 1L), flows.get(0).inputSeed());
        assertEquals(java.util.Map.of(b, 1L), flows.get(0).outputSeed());
        assertEquals(java.util.Map.of(b, 1L), flows.get(1).inputSeed());
        assertEquals(java.util.Map.of(a, 1L), flows.get(1).outputSeed());
    }

    @Test
    void twoPatternsCanCarryTwoMaterialLoopsAsOneAtomicSeedState() {
        var a = key("two_loop_a");
        var b = key("two_loop_b");
        var c = key("two_loop_c");
        var d = key("two_loop_d");
        var first = pattern(List.of(output(b, 1), output(d, 1)),
                input(a, 1, null), input(c, 1, null));
        var second = pattern(List.of(output(a, 2), output(c, 1)),
                input(b, 1, null), input(d, 1, null));
        var details = List.<IPatternDetails>of(first, second);
        var members = List.of(
                new ClosedLoopPatternAnalyzer.Member(first, 1),
                new ClosedLoopPatternAnalyzer.Member(second, 1));

        assertEquals(ClosedLoopPatternAnalyzer.StructureStatus.VALID,
                ClosedLoopPatternAnalyzer.validateMinimalStructure(details));
        var analysis = ClosedLoopPatternAnalyzer.analyze(members, a);
        assertNotNull(analysis);
        assertEquals(2, analysis.seeds().size());
        assertAmount(analysis.seeds(), a, 1);
        assertAmount(analysis.seeds(), c, 1);

        var flows = ClosedLoopPatternAnalyzer.deriveMemberFlows(members, analysis.seeds());
        assertEquals(java.util.Map.of(a, 1L, c, 1L), flows.get(0).inputSeed());
        assertEquals(java.util.Map.of(b, 1L, d, 1L), flows.get(0).outputSeed());
        assertEquals(java.util.Map.of(b, 1L, d, 1L), flows.get(1).inputSeed());
        assertEquals(java.util.Map.of(a, 1L, c, 1L), flows.get(1).outputSeed());
    }

    @Test
    void dependentSelfGrowthCannotClassifyItsExternalInputAsSeed() {
        var a = key("flow_external_a");
        var b = key("flow_seed_b");
        var growB = pattern(List.of(output(b, 2)),
                input(b, 1, null), input(a, 1, null));

        var flows = ClosedLoopPatternAnalyzer.deriveMemberFlows(
                List.of(new ClosedLoopPatternAnalyzer.Member(growB, 1)),
                List.of(output(b, 1)));

        assertEquals(java.util.Map.of(b, 1L), flows.get(0).inputSeed());
        assertEquals(java.util.Map.of(b, 1L), flows.get(0).outputSeed());
    }

    private static com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopAnalysis analyzeOverloadEdge(
            AEKey inputKey, AEKey outputKey, AEKey product, boolean inputFuzzy, boolean outputFuzzy) {
        var member = new FakeOverloadPattern(
                new IPatternDetails.IInput[] {input(inputKey, 1, null)},
                List.of(output(outputKey, 1), output(product, 1)), inputFuzzy, outputFuzzy);
        return ClosedLoopPatternAnalyzer.analyze(
                List.of(new ClosedLoopPatternAnalyzer.Member(member, 1)), product);
    }

    private static FakePattern pattern(List<GenericStack> outputs, IPatternDetails.IInput... inputs) {
        return new FakePattern(inputs, outputs);
    }

    private static FakeInput input(AEKey key, long amount, AEKey remaining) {
        return new FakeInput(new GenericStack[] {output(key, amount)}, remaining);
    }

    private static GenericStack output(AEKey key, long amount) {
        return new GenericStack(key, amount);
    }

    private static TestKey key(String id) {
        return new TestKey(id);
    }

    private static void assertStack(List<GenericStack> stacks, AEKey key, long amount) {
        assertEquals(1, stacks.size());
        assertAmount(stacks, key, amount);
    }

    private static void assertAmount(List<GenericStack> stacks, AEKey key, long amount) {
        assertEquals(amount, stacks.stream()
                .filter(stack -> key.equals(stack.what()))
                .mapToLong(GenericStack::amount)
                .sum());
    }

    private record FakePattern(IPatternDetails.IInput[] inputs, List<GenericStack> outputs)
            implements IPatternDetails {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IPatternDetails.IInput[] getInputs() { return inputs; }
        @Override public List<GenericStack> getOutputs() { return outputs; }
    }

    private record FakeOverloadPattern(
            IPatternDetails.IInput[] inputs, List<GenericStack> outputs,
            boolean inputFuzzy, boolean outputFuzzy)
            implements IPatternDetails, OverloadedProviderOnlyPatternDetails {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IPatternDetails.IInput[] getInputs() { return inputs; }
        @Override public List<GenericStack> getOutputs() { return outputs; }
        @Override public PatternExecutionHostKind requiredHostKind() {
            return PatternExecutionHostKind.OVERLOADED_PATTERN_PROVIDER;
        }
        @Override public String overloadPatternIdentity() {
            return "test:" + inputFuzzy + ":" + outputFuzzy;
        }
        @Override public OverloadPatternDetails overloadPatternDetailsView() { return null; }
        @Override public boolean hasFuzzyInputs() { return inputFuzzy; }
        @Override public boolean isFuzzyInput(int slot) { return inputFuzzy && slot == 0; }
        @Override public boolean isFuzzyOutput(int slot) { return outputFuzzy && slot == 0; }
    }

    private record FakeInput(GenericStack[] possibleInputs, AEKey remaining)
            implements IPatternDetails.IInput {
        @Override public GenericStack[] getPossibleInputs() { return possibleInputs; }
        @Override public long getMultiplier() { return 1; }
        @Override public boolean isValid(AEKey key, Level level) {
            return possibleInputs[0].what().equals(key);
        }
        @Override public AEKey getRemainingKey(AEKey template) { return remaining; }
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;
        private final String secondary;

        private TestKey(String id) { this(id, ""); }
        private TestKey(String id, String secondary) {
            this.id = id;
            this.secondary = secondary;
        }
        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() {
            return secondary.isEmpty() ? this : new TestKey(id);
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
        @Override protected Component computeDisplayName() { return Component.literal(id + secondary); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        @Override public boolean hasComponents() { return false; }
        @Override public boolean equals(Object obj) {
            return obj instanceof TestKey other && id.equals(other.id) && secondary.equals(other.secondary);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, secondary); }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(ResourceLocation.fromNamespaceAndPath("ae2lt_test", "key"), TestKey.class,
                    Component.literal("test key"));
        }
        @Override public MapCodec<? extends AEKey> codec() { return null; }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return null; }
    }
}
