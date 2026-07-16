package com.moakiee.thunderbolt.ae2.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import com.moakiee.thunderbolt.ae2.timewheel.ReusableSeedPattern;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPoolHost;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelPoolRestrictedPattern;
import com.moakiee.thunderbolt.core.planner.ReusableStockUsageKey;
import com.mojang.serialization.MapCodec;
import java.util.LinkedHashMap;
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
import org.junit.jupiter.api.Test;

class LoopCraftingPlanTest {
    @Test
    void exactFastPathUsageRetainsEachDedicatedPoolWhileFallbackBorrowsNothing() {
        var seed = new TestKey("seed");
        var left = new TestLoopPattern(seed, UUID.randomUUID(), false);
        var right = new TestLoopPattern(seed, UUID.randomUUID(), false);
        var patternTimes = new LinkedHashMap<IPatternDetails, Long>();
        patternTimes.put(left, 1L);
        patternTimes.put(right, 1L);
        var delegate = new CraftingPlan(
                new GenericStack(seed, 1L), 0L, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), patternTimes);

        var fallback = (LoopCraftingPlan) LoopCraftingPlan.wrapIfNeeded(delegate);
        assertTrue(fallback.hostReusableSeeds().isEmpty(),
                "an AE2 fallback plan has no exact private-stock trace and must not guess");

        Map<ReusableStockUsageKey<AEKey>, Long> usage = Map.of(
                usage(left, seed), 1L,
                usage(right, seed), 1L);
        var fast = (LoopCraftingPlan) LoopCraftingPlan.wrapIfNeeded(delegate, usage);

        assertEquals(Map.of(seed, 2L), fast.hostReusableSeeds());
        assertEquals(2, fast.hostReusableSeedAllocations().size());
        assertEquals(Set.of(left.reusableSeedGroupId(), right.reusableSeedGroupId()),
                fast.hostReusableSeedAllocations().stream()
                        .map(LoopCraftingPlan.HostReusableSeedAllocation::reusableSeedGroupId)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void routePrivateFuzzyHostSeedsMayExceedTheFinalSharedExactQuota() {
        var planned = new TestKey("planned_seed");
        var actualX = new TestKey("actual_x");
        var actualY = new TestKey("actual_y");
        var first = new TestLoopPattern(
                planned, UUID.randomUUID(), true, Set.of(planned, actualX));
        var second = new TestLoopPattern(
                planned, UUID.randomUUID(), true, Set.of(planned, actualY));
        var patternTimes = new LinkedHashMap<IPatternDetails, Long>();
        patternTimes.put(first, 1L);
        patternTimes.put(second, 1L);
        var delegate = new CraftingPlan(
                new GenericStack(planned, 1L), 0L, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), patternTimes);
        var firstSource = first.reusableStockSource();
        var secondSource = second.reusableStockSource();
        Map<ReusableStockUsageKey<AEKey>, Long> usage = Map.of(
                new ReusableStockUsageKey<>(
                        firstSource.storageScope(), firstSource.poolScope(),
                        firstSource.routingScope(), planned, actualX), 1L,
                new ReusableStockUsageKey<>(
                        secondSource.storageScope(), secondSource.poolScope(),
                        secondSource.routingScope(), planned, actualY), 1L);

        var plan = (LoopCraftingPlan) LoopCraftingPlan.wrapIfNeeded(delegate, usage);

        assertEquals(Map.of(planned, 1L), plan.totalReusableSeeds(),
                "two safe groups still return only one shared exact seed quota");
        assertEquals(Map.of(planned, 2L), plan.hostReusableSeeds(),
                "each non-exact physical variant remains route-private at startup");
        assertEquals(Set.of(actualX, actualY), plan.hostReusableSeedAllocations().stream()
                .map(LoopCraftingPlan.HostReusableSeedAllocation::actualKey)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(plan.hostReusableSeedAllocations().stream()
                .allMatch(allocation -> plan.acceptsReusableSeedVariant(
                        allocation, allocation.actualKey())));
    }

    @Test
    void splitActualVariantsCannotExceedOneRoutesSeedRequirement() {
        var planned = new TestKey("planned_seed");
        var actualX = new TestKey("actual_x");
        var actualY = new TestKey("actual_y");
        var pattern = new TestLoopPattern(
                planned, UUID.randomUUID(), true, Set.of(planned, actualX, actualY));
        var delegate = new CraftingPlan(
                new GenericStack(planned, 1L), 0L, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, 1L));
        var source = pattern.reusableStockSource();
        Map<ReusableStockUsageKey<AEKey>, Long> usage = Map.of(
                new ReusableStockUsageKey<>(
                        source.storageScope(), source.poolScope(), source.routingScope(),
                        planned, actualX), 1L,
                new ReusableStockUsageKey<>(
                        source.storageScope(), source.poolScope(), source.routingScope(),
                        planned, actualY), 1L);

        assertThrows(IllegalStateException.class,
                () -> LoopCraftingPlan.wrapIfNeeded(delegate, usage));
    }

    private static ReusableStockUsageKey<AEKey> usage(TestLoopPattern pattern, AEKey seed) {
        var source = pattern.reusableStockSource();
        // Mirror CraftPlannerV2's exact trace: dedicated loops share physical storage
        // but retain their group UUID as routing scope, and exact matching keeps the
        // planned and actual variants identical.
        return new ReusableStockUsageKey<>(
                source.storageScope(), source.poolScope(), source.routingScope(), seed, seed);
    }

    private static final class TestLoopPattern
            implements IPatternDetails, TimeWheelPoolRestrictedPattern, ReusableSeedPattern {
        private final AEKey seed;
        private final UUID groupId;
        private final boolean shared;
        private final Set<AEKey> acceptedVariants;

        private TestLoopPattern(AEKey seed, UUID groupId, boolean shared) {
            this(seed, groupId, shared, Set.of(seed));
        }

        private TestLoopPattern(
                AEKey seed, UUID groupId, boolean shared, Set<AEKey> acceptedVariants) {
            this.seed = seed;
            this.groupId = groupId;
            this.shared = shared;
            this.acceptedVariants = Set.copyOf(acceptedVariants);
        }

        @Override public AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return new IInput[0]; }
        @Override public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(seed, 1L));
        }
        @Override public boolean acceptsTimeWheelPool(TimeWheelCraftingCpuPoolHost host) {
            return true;
        }
        @Override public Object reusableSeedStorageScope() { return "host"; }
        @Override public UUID reusableSeedGroupId() { return groupId; }
        @Override public Set<AEKey> reusableSeedCycleKeys() { return Set.of(seed); }
        @Override public boolean hasSingleSeedInputPerMember() { return shared; }
        @Override public Map<AEKey, Long> totalReusableSeedRequirements() {
            return Map.of(seed, 1L);
        }
        @Override public boolean acceptsReusableSeedVariant(AEKey planned, AEKey actual) {
            return seed.equals(planned) && acceptedVariants.contains(actual);
        }
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
            return ResourceLocation.fromNamespaceAndPath("thunderbolt_test", id);
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
            super(ResourceLocation.fromNamespaceAndPath("thunderbolt_test", "loop_key"),
                    TestKey.class, Component.literal("loop key"));
        }
        @Override public MapCodec<? extends AEKey> codec() { return null; }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return null; }
    }
}
