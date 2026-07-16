package com.moakiee.ae2lt.logic.tianshu.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.thunderbolt.ae2.api.crafting.ISeedPreservingCraftingTask;
import com.moakiee.thunderbolt.ae2.api.crafting.IPlannedSeedSlotPattern;
import com.moakiee.thunderbolt.ae2.crafting.ExecuteLoopPattern;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadPatternDetails;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.ae2.overload.pattern.PatternExecutionHostKind;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelTaskPersistenceDefinition;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
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
        var groupId = UUID.randomUUID();
        var consumerId = UUID.randomUUID();
        var delegate = new FakeLoopPattern(
                new IPatternDetails.IInput[] {
                        input(true, stack(plannedSeed, 1L)),
                        input(false, stack(ordinaryInput, 5L))
                },
                Set.of(0),
                groupId);
        var wrapped = new ExecuteLoopPattern(
                delegate,
                consumerId,
                counter(plannedSeed, 1L),
                counter(plannedSeed, 1L),
                Map.of(consumerId, counter(plannedSeed, 1L)));
        var holders = new KeyCounter[] {
                counter(actualSeed, 1L),
                counter(ordinaryInput, 5L)
        };

        assertEquals(consumerId, wrapped.seedConsumerId());
        assertEquals(1L, wrapped.initialSeed().get(plannedSeed));
        assertEquals(Map.of(consumerId, 1L), wrapped.outputSeedConsumers(plannedSeed));
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
        var consumerId = UUID.randomUUID();
        var delegate = new FakeLoopPattern(
                new IPatternDetails.IInput[] {
                        input(false, stack(plannedSeed, 1L), stack(alternativeSeed, 1L))
                },
                Set.of(),
                UUID.randomUUID());
        var wrapped = new ExecuteLoopPattern(
                delegate,
                consumerId,
                counter(plannedSeed, 1L),
                counter(plannedSeed, 1L),
                Map.of(consumerId, counter(plannedSeed, 1L)));

        assertEquals(consumerId, wrapped.seedConsumerId());
        assertEquals(1L, wrapped.initialSeed().get(plannedSeed));
        assertEquals(Map.of(consumerId, 1L), wrapped.outputSeedConsumers(plannedSeed));
        assertTrue(wrapped.requiresActualSeedKeyTracking());
        assertTrue(wrapped.isInputSeedKey(alternativeSeed));
        assertEquals(1L,
                wrapped.actualInputSeed(new KeyCounter[] {counter(alternativeSeed, 1L)})
                        .get(alternativeSeed));
        assertFalse(wrapped.resolveActualInputSeedUses(
                        new KeyCounter[] {counter(alternativeSeed, 2L)}).complete(),
                "every physical unit extracted from a seed slot must be ledgered");
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

    @Test
    void fuzzySeedCandidatesWithDifferentPhysicalAmountsAreRejected() {
        var plannedSeed = key("cell", "planned");
        var first = key("cell", "first");
        var second = key("cell", "second");
        var delegate = new FakeLoopPattern(
                new IPatternDetails.IInput[] {
                        input(false, stack(first, 1L), stack(second, 2L))
                },
                Set.of(0),
                UUID.randomUUID());

        assertThrows(IllegalArgumentException.class,
                () -> ClosedLoopExpandedPatternDetails.pinReusableSeedInputs(
                        delegate, Set.of(plannedSeed)));
    }

    @Test
    void componentVariantSeedsRemainInDedicatedConsumerLedgers() {
        var planned = key("shared_state", "planned");
        var alternative = key("shared_state", "alternative");

        assertTrue(Ae2ClosedLoopPatternDetails.isSharedSeedPoolSafe(
                true, Set.of(planned), Map.of(planned, Set.of(planned)), Set.of()));
        assertFalse(Ae2ClosedLoopPatternDetails.isSharedSeedPoolSafe(
                true, Set.of(planned), Map.of(planned, Set.of(planned, alternative)), Set.of()));
        assertFalse(Ae2ClosedLoopPatternDetails.isSharedSeedPoolSafe(
                true, Set.of(planned), Map.of(planned, Set.of(planned)), Set.of(planned)));
    }

    @Test
    void plannedSeedSlotMappingSeparatesAnotherSlotsExactKeyFromAnAlternative() {
        var a = key("mapped_a", "");
        var b = key("mapped_b", "");
        var delegate = new FakeLoopPattern(
                new IPatternDetails.IInput[] {
                        input(false, stack(a, 1L), stack(b, 1L)),
                        input(false, stack(b, 1L))
                },
                Set.of(), UUID.randomUUID(), Map.of(0, a, 1, b));
        var wrapped = new ExecuteLoopPattern(
                delegate, UUID.randomUUID(), counter(a, 1L),
                counter(Map.of(a, 1L, b, 1L)), Map.of());

        var resolution = wrapped.resolveActualInputSeedUses(new KeyCounter[] {
                counter(b, 1L), counter(b, 1L)
        });

        assertTrue(resolution.complete());
        assertEquals(List.of(a, b), resolution.uses().stream()
                .map(ExecuteLoopPattern.ActualSeedUse::planned).toList());
    }

    @Test
    void hostVariantRuleUsesTheIntersectionOfSlotsSharingOnePlannedKey() {
        var planned = key("capacity_a", "");
        var x = key("capacity_x", "");
        var y = key("capacity_y", "");
        var delegate = new FakeLoopPattern(
                new IPatternDetails.IInput[] {
                        input(false, stack(planned, 1L), stack(x, 1L)),
                        input(false, stack(planned, 1L), stack(y, 1L))
                },
                Set.of(), UUID.randomUUID(), Map.of(0, planned, 1, planned));
        var wrapped = new ExecuteLoopPattern(
                delegate, UUID.randomUUID(), counter(planned, 2L),
                counter(planned, 2L), Map.of());

        var rule = wrapped.seedVariantRule(planned);
        assertTrue(rule.accepts(planned));
        assertFalse(rule.accepts(x), "X cannot satisfy the second slot");
        assertFalse(rule.accepts(y), "Y cannot satisfy the first slot");
        assertEquals(List.of(planned), java.util.Arrays.stream(
                        wrapped.getInputs()[0].getPossibleInputs())
                .map(GenericStack::what).toList());
        assertFalse(wrapped.getInputs()[0].isValid(x, null),
                "execution must not greedily consume a variant excluded by the safe intersection");
    }

    @Test
    void hostVariantsRequireAnIndivisibleMultiUnitP2BundleToRemainExact() {
        var a = key("host_bundle_a", "planned");
        var flow = new ClosedLoopPatternAnalyzer.MemberFlow(
                Map.of(a, 2L), Map.of(a, 2L), Map.of(0, a));
        var routing = ClosedLoopConsumerRouting.compile(UUID.randomUUID(), List.of(flow));

        assertEquals(Set.of(a), Ae2ClosedLoopPatternDetails.exactOnlyHostSeedKeys(
                List.of(flow), List.of(1L), routing),
                "A1 + X1 must not masquerade as one usable 2A P2 bundle");
        assertTrue(Ae2ClosedLoopPatternDetails.exactOnlyHostSeedKeys(
                        List.of(flow), List.of(2L), routing).isEmpty(),
                "two independent one-unit P2 executions may each accept a concrete variant");
    }

    @Test
    void idOnlySeedOutputCannotBeFifoRoutedIntoAStrictP2Consumer() {
        var a = key("output_mode_a", "planned");
        var b = key("output_mode_b", "");
        var producer = new FakeLoopPattern(
                new IPatternDetails.IInput[] {input(false, stack(b, 1L))},
                Set.of(), UUID.randomUUID(), Map.of(0, b),
                List.of(stack(a, 1L)), Set.of(0));
        var strictConsumer = new FakeLoopPattern(
                new IPatternDetails.IInput[] {input(false, stack(a, 1L))},
                Set.of(), UUID.randomUUID(), Map.of(0, a),
                List.of(stack(b, 1L)), Set.of());
        var fuzzyConsumer = new FakeLoopPattern(
                new IPatternDetails.IInput[] {input(true, stack(a, 1L))},
                Set.of(0), UUID.randomUUID(), Map.of(0, a),
                List.of(stack(b, 1L)), Set.of());
        var producerFlow = new ClosedLoopPatternAnalyzer.MemberFlow(
                Map.of(b, 1L), Map.of(a, 1L), Map.of(0, b));
        var consumerFlow = new ClosedLoopPatternAnalyzer.MemberFlow(
                Map.of(a, 1L), Map.of(b, 1L), Map.of(0, a));

        assertThrows(IllegalArgumentException.class,
                () -> Ae2ClosedLoopPatternDetails.validateFuzzyOutputSeedConsumers(
                        List.of(
                                new ClosedLoopPatternAnalyzer.Member(producer, 1L),
                                new ClosedLoopPatternAnalyzer.Member(strictConsumer, 1L)),
                        List.of(producerFlow, consumerFlow)));
        assertDoesNotThrow(
                () -> Ae2ClosedLoopPatternDetails.validateFuzzyOutputSeedConsumers(
                        List.of(
                                new ClosedLoopPatternAnalyzer.Member(producer, 1L),
                                new ClosedLoopPatternAnalyzer.Member(fuzzyConsumer, 1L)),
                        List.of(producerFlow, consumerFlow)));
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

    private static KeyCounter counter(Map<AEKey, Long> amounts) {
        var result = new KeyCounter();
        for (var entry : amounts.entrySet()) result.add(entry.getKey(), entry.getValue());
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
            UUID groupId,
            Map<Integer, AEKey> plannedSeedSlots,
            List<GenericStack> outputs,
            Set<Integer> fuzzyOutputSlots)
            implements IPatternDetails, OverloadedProviderOnlyPatternDetails,
            ISeedPreservingCraftingTask, TimeWheelTaskPersistenceDefinition,
            IPlannedSeedSlotPattern {
        private FakeLoopPattern(
                IPatternDetails.IInput[] inputs, Set<Integer> fuzzySlots, UUID groupId) {
            this(inputs, fuzzySlots, groupId, Map.of(), List.of(), Set.of());
        }
        private FakeLoopPattern(
                IPatternDetails.IInput[] inputs,
                Set<Integer> fuzzySlots,
                UUID groupId,
                Map<Integer, AEKey> plannedSeedSlots) {
            this(inputs, fuzzySlots, groupId, plannedSeedSlots, List.of(), Set.of());
        }
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IPatternDetails.IInput[] getInputs() { return inputs.clone(); }
        @Override public List<GenericStack> getOutputs() { return List.copyOf(outputs); }
        @Override public PatternExecutionHostKind requiredHostKind() {
            return PatternExecutionHostKind.OVERLOADED_PATTERN_PROVIDER;
        }
        @Override public String overloadPatternIdentity() { return "test:fuzzy-loop"; }
        @Override public OverloadPatternDetails overloadPatternDetailsView() { return null; }
        @Override public boolean hasFuzzyInputs() { return !fuzzySlots.isEmpty(); }
        @Override public boolean isFuzzyInput(int slot) { return fuzzySlots.contains(slot); }
        @Override public boolean isFuzzyOutput(int slot) {
            return fuzzyOutputSlots.contains(slot);
        }
        @Override public UUID reusableSeedGroupId() { return groupId; }
        @Override public Set<AEKey> reusableSeedCycleKeys() { return Set.of(); }
        @Override public boolean hasSingleSeedInputPerMember() { return true; }
        @Override public AEItemKey timeWheelPersistenceDefinition() { return null; }
        @Override public Map<Integer, AEKey> plannedSeedInputSlots() {
            return Map.copyOf(plannedSeedSlots);
        }
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
