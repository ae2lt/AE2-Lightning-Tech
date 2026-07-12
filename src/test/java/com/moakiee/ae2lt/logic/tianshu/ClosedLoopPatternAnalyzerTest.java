package com.moakiee.ae2lt.logic.tianshu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternAnalyzer;
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
        assertAmount(analysis.seeds(), catalyst, 1);
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
