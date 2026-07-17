package com.moakiee.ae2lt.logic.tianshu.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadPatternDetails;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.ae2.overload.pattern.PatternExecutionHostKind;
import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
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

class ClosedLoopDiscoveryServiceTest {
    @Test
    void enumeratesAlternativeLocalLoopsAndAcceptsSecondaryTargetOutput() {
        var target = key("target");
        var intermediate = key("intermediate");
        var byproduct = key("byproduct");
        var materialA = key("material_a");
        var materialB = key("material_b");

        var root = pattern(
                List.of(stack(byproduct, 1), stack(target, 2)),
                input(intermediate, 1, null));
        var pathA = pattern(
                List.of(stack(intermediate, 1)),
                input(target, 1, null), input(materialA, 1, null));
        var pathB = pattern(
                List.of(stack(intermediate, 1)),
                input(target, 1, null), input(materialB, 1, null));
        var patterns = Map.<AEKey, List<IPatternDetails>>of(
                target, List.of(root),
                intermediate, List.of(pathA, pathB));

        var candidates = ClosedLoopDiscoveryService.resolveCandidates(
                key -> patterns.getOrDefault(key, List.of()), target);

        assertEquals(2, candidates.size());
        assertEquals(List.of(materialA, materialB), candidates.stream()
                .map(candidate -> candidate.analysis().externalInputs().stream()
                        .filter(stack -> stack.what().equals(materialA) || stack.what().equals(materialB))
                        .findFirst().orElseThrow().what())
                .sorted(java.util.Comparator.comparing(key -> key.getId().toString()))
                .toList());
        for (var candidate : candidates) {
            assertEquals(2, candidate.members().size());
            assertEquals(1, amount(candidate.analysis().seeds(), intermediate));
            assertEquals(1, amount(candidate.analysis().netOutputs(), target));
            assertEquals(1, amount(candidate.analysis().netOutputs(), byproduct));
        }
    }

    @Test
    void recognizesSinglePatternReturnedCatalystLoop() {
        var catalyst = key("catalyst");
        var material = key("material");
        var output = key("output");
        var pattern = pattern(
                List.of(stack(output, 1)),
                input(catalyst, 1, catalyst), input(material, 4, null));

        var candidates = ClosedLoopDiscoveryService.resolveCandidates(
                key -> key.equals(output) ? List.of(pattern) : List.of(), output);

        assertEquals(1, candidates.size());
        assertEquals(1, amount(candidates.getFirst().analysis().seeds(), catalyst));
        assertEquals(4, amount(candidates.getFirst().analysis().externalInputs(), material));
    }

    @Test
    void discoversASelfGrowthLoopThatFeedsADownstreamTarget() {
        var seed = key("downstream_seed");
        var target = key("downstream_target");
        var downstream = pattern(List.of(stack(target, 1)), input(seed, 1, null));
        var selfGrowth = pattern(List.of(stack(seed, 2)), input(seed, 1, null));
        var patterns = Map.<AEKey, List<IPatternDetails>>of(
                target, List.of(downstream), seed, List.of(selfGrowth));

        var candidates = ClosedLoopDiscoveryService.resolveCandidates(
                key -> patterns.getOrDefault(key, List.of()), target);

        assertEquals(1, candidates.size());
        var candidate = candidates.getFirst();
        assertEquals(2, candidate.members().size());
        assertEquals(1, amount(candidate.analysis().seeds(), seed));
        assertEquals(1, amount(candidate.analysis().netOutputs(), target));
    }

    @Test
    void discoversAnOuterLoopThroughAClosedLoopMacroWithoutInheritingItsMultipliers() {
        var innerSeed = key("nested_discovery_seed");
        var intermediate = key("nested_discovery_intermediate");
        var target = key("nested_discovery_target");
        var innerPayload = new ClosedLoopPatternPayload(
                UUID.randomUUID(), 1L,
                List.of(new ClosedLoopMemberPattern(
                        new SourcePatternSnapshot(
                                ResourceLocation.fromNamespaceAndPath(
                                        "ae2lt_test", "nested_discovery_leaf"),
                                null, null),
                        1L)),
                List.of(stack(innerSeed, 1L)),
                List.of(),
                List.of(stack(intermediate, 1L)),
                37, 41, true);
        var innerMacro = new FakeClosedLoopPattern(
                innerPayload,
                new IPatternDetails.IInput[] {input(innerSeed, 37L, null)});
        var downstream = pattern(
                List.of(stack(target, 1L)), input(intermediate, 1L, null));
        var patterns = Map.<AEKey, List<IPatternDetails>>of(
                target, List.of(downstream),
                intermediate, List.of(innerMacro));

        var candidates = ClosedLoopDiscoveryService.resolveCandidates(
                key -> patterns.getOrDefault(key, List.of()), target);

        var candidate = candidates.stream()
                .filter(value -> value.members().size() == 2)
                .findFirst().orElseThrow();
        assertEquals(1L, amount(candidate.analysis().seeds(), innerSeed));
        assertEquals(0L, amount(candidate.analysis().externalInputs(), innerSeed));
        assertEquals(1L, amount(candidate.analysis().netOutputs(), target));
    }

    @Test
    void discoversCrossConnectedSeedCyclesWithoutReportingTheirBridgeAsExternal() {
        var a = key("cross_discovery_a");
        var b = key("cross_discovery_b");
        var c = key("cross_discovery_c");
        var d = key("cross_discovery_d");
        var aToTwoB = pattern(List.of(stack(b, 2)), input(a, 1, null));
        var cToTwoD = pattern(List.of(stack(d, 2)), input(c, 1, null));
        var bToA = pattern(List.of(stack(a, 1)), input(b, 1, null));
        var bAndDToC = pattern(List.of(stack(c, 1)),
                input(b, 1, null), input(d, 1, null));
        var patterns = Map.<AEKey, List<IPatternDetails>>of(
                a, List.of(bToA),
                b, List.of(aToTwoB),
                c, List.of(bAndDToC),
                d, List.of(cToTwoD));

        var candidates = ClosedLoopDiscoveryService.resolveCandidates(
                key -> patterns.getOrDefault(key, List.of()), d);

        var composite = candidates.stream()
                .filter(candidate -> candidate.members().size() == 4)
                .filter(candidate -> candidate.analysis().externalInputs().isEmpty())
                .findFirst().orElseThrow();
        assertEquals(1, amount(composite.analysis().seeds(), a));
        assertEquals(1, amount(composite.analysis().seeds(), c));
        assertEquals(1, amount(composite.analysis().netOutputs(), d));
    }

    @Test
    void discoversCrossInputMultiLoopAndMovesTheDProducerBeforeItsConsumers() {
        var a = key("cross_input_discovery_a");
        var b = key("cross_input_discovery_b");
        var c = key("cross_input_discovery_c");
        var d = key("cross_input_discovery_d");
        var e = key("cross_input_discovery_e");
        var aAndDToTwoBAndE = pattern(
                List.of(stack(b, 2), stack(e, 1)),
                input(a, 1, null), input(d, 1, null));
        var cToTwoD = pattern(List.of(stack(d, 2)), input(c, 1, null));
        var bToA = pattern(List.of(stack(a, 1)), input(b, 1, null));
        var bAndDToC = pattern(List.of(stack(c, 1)),
                input(b, 1, null), input(d, 1, null));
        var patterns = Map.<AEKey, List<IPatternDetails>>of(
                a, List.of(bToA),
                b, List.of(aAndDToTwoBAndE),
                c, List.of(bAndDToC),
                d, List.of(cToTwoD),
                e, List.of(aAndDToTwoBAndE));

        var candidates = ClosedLoopDiscoveryService.resolveCandidates(
                key -> patterns.getOrDefault(key, List.of()), e);

        var composite = candidates.stream()
                .filter(candidate -> candidate.members().size() == 4)
                .filter(candidate -> candidate.analysis().externalInputs().isEmpty())
                .findFirst().orElseThrow();
        assertEquals(1,
                amount(composite.analysis().seeds(), a)
                        + amount(composite.analysis().seeds(), b));
        assertEquals(1, amount(composite.analysis().seeds(), c));
        assertEquals(0, amount(composite.analysis().seeds(), d));
        assertEquals(1, amount(composite.analysis().netOutputs(), e));
    }

    @Test
    void solvesNonUnitCoefficientsBeforeContractingTheLoop() {
        var target = key("ratio_target");
        var intermediate = key("ratio_intermediate");
        var root = pattern(List.of(stack(target, 3)), input(intermediate, 2, null));
        var recycle = pattern(List.of(stack(intermediate, 1)), input(target, 1, null));
        var patterns = Map.<AEKey, List<IPatternDetails>>of(
                target, List.of(root), intermediate, List.of(recycle));

        var candidates = ClosedLoopDiscoveryService.resolveCandidates(
                key -> patterns.getOrDefault(key, List.of()), target);

        assertEquals(1, candidates.size());
        var candidate = candidates.getFirst();
        assertEquals(List.of(1L, 2L), java.util.Arrays.stream(candidate.coefficients()).boxed().toList());
        assertEquals(2, amount(candidate.analysis().seeds(), intermediate));
        assertEquals(1, amount(candidate.analysis().netOutputs(), target));
        assertEquals(0, amount(candidate.analysis().externalInputs(), intermediate));
    }

    @Test
    void findsValidCoefficientBeyondTheLegacyBound() {
        var target = key("bounded_target");
        var intermediate = key("bounded_intermediate");
        var root = pattern(List.of(stack(target, 10)), input(intermediate, 9, null));
        var recycle = pattern(List.of(stack(intermediate, 1)), input(target, 1, null));
        var patterns = Map.<AEKey, List<IPatternDetails>>of(
                target, List.of(root), intermediate, List.of(recycle));

        var candidates = ClosedLoopDiscoveryService.resolveCandidates(
                key -> patterns.getOrDefault(key, List.of()), target);

        assertEquals(1, candidates.size());
        assertEquals(List.of(1L, 9L), java.util.Arrays.stream(
                candidates.getFirst().coefficients()).boxed().toList());
        assertEquals(9, amount(candidates.getFirst().analysis().seeds(), intermediate));
        assertEquals(1, amount(candidates.getFirst().analysis().netOutputs(), target));
    }

    @Test
    void discoversLoopThroughANonFirstSubstitutionCandidate() {
        var target = key("substitution_target");
        var unavailable = key("substitution_unavailable");
        var intermediate = key("substitution_intermediate");
        var root = pattern(
                List.of(stack(target, 2)),
                alternatives(stack(unavailable, 1), stack(intermediate, 1)));
        var recycle = pattern(List.of(stack(intermediate, 1)), input(target, 1, null));
        var patterns = Map.<AEKey, List<IPatternDetails>>of(
                target, List.of(root), intermediate, List.of(recycle));

        var candidates = ClosedLoopDiscoveryService.resolveCandidates(
                key -> patterns.getOrDefault(key, List.of()), target);

        assertEquals(1, candidates.size());
        assertEquals(2, candidates.getFirst().members().size());
        assertEquals(1, amount(candidates.getFirst().analysis().seeds(), intermediate));
        assertEquals(1, amount(candidates.getFirst().analysis().netOutputs(), target));
    }

    @Test
    void discoversIdOnlyLoopThroughAnotherConcreteVariant() {
        var target = key("id_only_target");
        var encodedSeed = new TestKey("id_only_seed", "encoded");
        var returnedSeed = new TestKey("id_only_seed", "returned");
        var root = new FakeOverloadPattern(
                new IPatternDetails.IInput[] {input(encodedSeed, 1, null)},
                List.of(stack(target, 2)), true, false);
        var recycle = new FakeOverloadPattern(
                new IPatternDetails.IInput[] {input(target, 1, null)},
                List.of(stack(returnedSeed, 1)), false, true);
        var patterns = Map.<AEKey, List<IPatternDetails>>of(
                target, List.of(root), returnedSeed, List.of(recycle));

        var candidates = ClosedLoopDiscoveryService.resolveCandidates(
                key -> patterns.getOrDefault(key, List.of()),
                template -> template.dropSecondary().equals(encodedSeed.dropSecondary())
                        ? List.of(returnedSeed) : List.of(),
                target);

        assertEquals(1, candidates.size());
        assertEquals(2, candidates.getFirst().members().size());
        assertEquals(1, amount(candidates.getFirst().analysis().seeds(), returnedSeed));
        assertEquals(1, amount(candidates.getFirst().analysis().netOutputs(), target));
    }

    @Test
    void doesNotUseAnIdOnlyOutputToSatisfyAStrictInput() {
        var target = key("strict_target");
        var seed = key("strict_seed");
        var root = pattern(List.of(stack(target, 2)), input(seed, 1, null));
        var recycle = new FakeOverloadPattern(
                new IPatternDetails.IInput[] {input(target, 1, null)},
                List.of(stack(seed, 1)), false, true);
        var patterns = Map.<AEKey, List<IPatternDetails>>of(
                target, List.of(root), seed, List.of(recycle));

        var candidates = ClosedLoopDiscoveryService.resolveCandidates(
                key -> patterns.getOrDefault(key, List.of()), target);

        assertEquals(0, candidates.size());
    }

    private static long amount(List<GenericStack> stacks, AEKey key) {
        return stacks.stream().filter(stack -> key.equals(stack.what()))
                .mapToLong(GenericStack::amount).sum();
    }

    private static FakePattern pattern(List<GenericStack> outputs, IPatternDetails.IInput... inputs) {
        return new FakePattern(inputs, outputs);
    }

    private static FakeInput input(AEKey key, long amount, AEKey remaining) {
        return new FakeInput(new GenericStack[] {stack(key, amount)}, remaining);
    }

    private static FakeInput alternatives(GenericStack... alternatives) {
        return new FakeInput(alternatives, null);
    }

    private static GenericStack stack(AEKey key, long amount) {
        return new GenericStack(key, amount);
    }

    private static TestKey key(String id) {
        return new TestKey(id);
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

    private record FakeClosedLoopPattern(
            ClosedLoopPatternPayload closedLoopPayload,
            IPatternDetails.IInput[] misleadingRuntimeInputs)
            implements TianshuClosedLoopPatternDetails {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IPatternDetails.IInput[] getInputs() {
            return misleadingRuntimeInputs;
        }
        @Override public List<GenericStack> getOutputs() {
            return closedLoopPayload.netOutputs();
        }
    }

    private record FakeInput(GenericStack[] possibleInputs, AEKey remaining)
            implements IPatternDetails.IInput {
        @Override public GenericStack[] getPossibleInputs() { return possibleInputs; }
        @Override public long getMultiplier() { return 1; }
        @Override public boolean isValid(AEKey key, Level level) {
            for (var possible : possibleInputs) {
                if (possible.what().equals(key)) return true;
            }
            return false;
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
        @Override protected Component computeDisplayName() { return Component.literal(id); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        @Override public boolean hasComponents() { return false; }
        @Override public boolean equals(Object obj) {
            return obj instanceof TestKey other
                    && id.equals(other.id) && secondary.equals(other.secondary);
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
