package com.moakiee.ae2lt.logic.tianshu.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

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
        private TestKey(String id) { this.id = id; }
        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag(net.minecraft.core.HolderLookup.Provider registries) {
            var tag = new CompoundTag();
            tag.putString("id", id);
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
            return obj instanceof TestKey other && id.equals(other.id);
        }
        @Override public int hashCode() { return id.hashCode(); }
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
