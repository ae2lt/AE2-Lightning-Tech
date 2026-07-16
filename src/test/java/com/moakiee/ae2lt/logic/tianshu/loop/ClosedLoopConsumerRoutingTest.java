package com.moakiee.ae2lt.logic.tianshu.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.moakiee.thunderbolt.core.planner.Sat;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ClosedLoopConsumerRoutingTest {
    private static final UUID GROUP_ID = UUID.fromString("17026a30-5b59-45e8-b944-3b588cf90a8d");

    @Test
    void selfGrowthRoutesOnlyTheRetainedAIntoTheSameConsumerNextCycle() {
        var a = key("a");

        // The second A from the physical A -> 2A output is public surplus. MemberFlow contains
        // only the one A retained as output seed, so it must not accidentally expand seed stock.
        var plan = compile(flow(Map.of(a, 1L), Map.of(a, 1L)));
        var consumer = plan.consumers().getFirst();

        assertEquals(Map.of(a, 1L), consumer.bootstrapSeed());
        assertEquals(Map.of(a, 1L), plan.bootstrapSeed());
        assertEquals(Map.of(consumer.consumerId(), Map.of(a, 1L)),
                plan.producers().getFirst().targets());
    }

    @Test
    void groupedBToATransitionRoutesForwardWithoutExpandingItsTwoCopies() {
        var a = key("grouped_a");
        var b = key("grouped_b");

        var plan = compile(
                flow(Map.of(a, 1L), Map.of(b, 2L)),
                flow(Map.of(b, 2L), Map.of(a, 1L)));
        var first = plan.consumers().get(0);
        var second = plan.consumers().get(1);

        assertEquals(Map.of(a, 1L), first.bootstrapSeed());
        assertEquals(Map.of(), second.bootstrapSeed());
        assertEquals(Map.of(second.consumerId(), Map.of(b, 2L)),
                plan.producers().get(0).targets());
        assertEquals(Map.of(first.consumerId(), Map.of(a, 1L)),
                plan.producers().get(1).targets());
    }

    @Test
    void oneProducerSplitsBIntoTheTwoConsumerOwnedAccounts() {
        var a = key("fork_a");
        var b = key("fork_b");
        var c = key("fork_c");
        var d = key("fork_d");

        var plan = compile(
                flow(Map.of(a, 1L), Map.of(b, 2L)),
                flow(Map.of(b, 1L), Map.of(c, 1L)),
                flow(Map.of(b, 1L), Map.of(d, 1L)),
                flow(Map.of(c, 1L, d, 1L), Map.of(a, 1L)));

        assertEquals(Map.of(
                        plan.consumers().get(1).consumerId(), Map.of(b, 1L),
                        plan.consumers().get(2).consumerId(), Map.of(b, 1L)),
                plan.producers().get(0).targets());
        assertEquals(Map.of(a, 1L), plan.bootstrapSeed());
    }

    @Test
    void independentBackEdgesBootstrapAAndCIntoTheirActualConsumers() {
        var a = key("multi_a");
        var b = key("multi_b");
        var c = key("multi_c");
        var d = key("multi_d");

        var plan = compile(
                flow(Map.of(a, 1L), Map.of(b, 1L)),
                flow(Map.of(c, 1L), Map.of(d, 1L)),
                flow(Map.of(b, 1L), Map.of(a, 1L)),
                flow(Map.of(d, 1L), Map.of(c, 1L)));

        assertEquals(Map.of(a, 1L, c, 1L), plan.bootstrapSeed());
        assertEquals(Map.of(a, 1L), plan.consumers().get(0).bootstrapSeed());
        assertEquals(Map.of(c, 1L), plan.consumers().get(1).bootstrapSeed());
        assertEquals(Map.of(), plan.consumers().get(2).bootstrapSeed());
        assertEquals(Map.of(), plan.consumers().get(3).bootstrapSeed());
    }

    @Test
    void consumerIdsAreStablePerGroupAndMember() {
        var a = key("stable_a");
        var first = compile(flow(Map.of(a, 1L), Map.of(a, 1L)));
        var second = compile(flow(Map.of(a, 1L), Map.of(a, 1L)));
        var otherGroup = ClosedLoopConsumerRouting.compile(
                UUID.fromString("1383c460-92e2-4b78-85dc-dc00c5dce51e"),
                List.of(flow(Map.of(a, 1L), Map.of(a, 1L))));

        assertEquals(first.consumers().getFirst().consumerId(),
                second.consumers().getFirst().consumerId());
        assertNotEquals(first.consumers().getFirst().consumerId(),
                otherGroup.consumers().getFirst().consumerId());
    }

    @Test
    void componentDistinctPlannedKeysAreNotCollapsedForFuzzyExecution() {
        var pristine = key("component_item", "pristine");
        var damaged = key("component_item", "damaged");
        assertEquals(pristine.dropSecondary(), damaged.dropSecondary());

        var plan = compile(
                flow(Map.of(pristine, 1L), Map.of(damaged, 1L)),
                flow(Map.of(damaged, 1L), Map.of(pristine, 1L)));

        assertEquals(Map.of(
                        plan.consumers().get(1).consumerId(), Map.of(damaged, 1L)),
                plan.producers().get(0).targets());
        assertEquals(Map.of(pristine, 1L), plan.bootstrapSeed());
    }

    @Test
    void amountsClampToSatWithoutPerCopyExpansion() {
        var a = key("saturated_a");
        var b = key("saturated_b");

        var plan = compile(
                flow(Map.of(a, Long.MAX_VALUE), Map.of(b, Long.MAX_VALUE)),
                flow(Map.of(b, Long.MAX_VALUE), Map.of(a, Long.MAX_VALUE)));

        assertEquals(Map.of(a, Sat.SAT), plan.bootstrapSeed());
        assertEquals(Map.of(
                        plan.consumers().get(1).consumerId(), Map.of(b, Sat.SAT)),
                plan.producers().get(0).targets());
    }

    private static ClosedLoopConsumerRouting.RoutingPlan compile(
            ClosedLoopPatternAnalyzer.MemberFlow... flows) {
        return ClosedLoopConsumerRouting.compile(GROUP_ID, List.of(flows));
    }

    private static ClosedLoopPatternAnalyzer.MemberFlow flow(
            Map<AEKey, Long> inputSeed, Map<AEKey, Long> outputSeed) {
        return new ClosedLoopPatternAnalyzer.MemberFlow(inputSeed, outputSeed);
    }

    private static TestKey key(String id) {
        return key(id, "");
    }

    private static TestKey key(String id, String secondary) {
        return new TestKey(id, secondary);
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
            var result = new CompoundTag();
            result.putString("id", id);
            result.putString("secondary", secondary);
            return result;
        }
        @Override public Object getPrimaryKey() { return id; }
        @Override public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("ae2lt_test", id);
        }
        @Override public void writeToPacket(RegistryFriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() {
            return Component.literal(id + ':' + secondary);
        }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        @Override public boolean hasComponents() { return !secondary.isEmpty(); }
        @Override public boolean equals(Object obj) {
            return obj instanceof TestKey other
                    && id.equals(other.id)
                    && secondary.equals(other.secondary);
        }
        @Override public int hashCode() { return 31 * id.hashCode() + secondary.hashCode(); }
        @Override public String toString() { return id + ':' + secondary; }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(ResourceLocation.fromNamespaceAndPath("ae2lt_test", "consumer_routing_key"),
                    TestKey.class, Component.literal("consumer routing key"));
        }
        @Override public MapCodec<? extends AEKey> codec() { return null; }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return null; }
    }
}
