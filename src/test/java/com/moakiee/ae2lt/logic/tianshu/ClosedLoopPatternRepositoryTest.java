package com.moakiee.ae2lt.logic.tianshu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.mojang.serialization.MapCodec;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternRepository;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopMemberPattern;
import com.moakiee.ae2lt.logic.tianshu.loop.TianshuSeedRefillService;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceDecision;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceRepository;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceRule;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockRepository;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceBadge;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceStatus;
import com.moakiee.ae2lt.logic.tianshu.maintenance.LayeredReservedStockPolicy;
import com.moakiee.ae2lt.logic.tianshu.maintenance.MaintenanceTopologyService;
import com.moakiee.ae2lt.logic.tianshu.maintenance.MaintenanceVariantService;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.thunderbolt.core.planner.CraftGraph;
import com.moakiee.thunderbolt.core.planner.CraftInput;
import com.moakiee.thunderbolt.core.planner.CraftPlannerV2;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ClosedLoopPatternRepositoryTest {
    @Test
    void seedMultiplierScalesStorageTargetButNotTheComputedBaseSeed() {
        var base = payload(UUID.randomUUID(), 1);
        var scaled = base.withSeedMultiplier(1_000);
        var seed = base.seeds().getFirst();

        assertEquals(1, seed.amount());
        assertEquals(1_000, scaled.seedMultiplier());
        assertEquals(1_000,
                TianshuSeedRefillService.requirements(scaled).get(seed.what()));
    }

    @Test
    void warehouseCapacityGatesNewPatternsButNeverDeletesExistingData() {
        var capacity = new int[] { 1 };
        var repository = new ClosedLoopPatternRepository(() -> capacity[0]);
        var first = payload(UUID.randomUUID(), 1);
        var second = payload(UUID.randomUUID(), 1);

        assertEquals(ClosedLoopPatternRepository.PutResult.ADDED, repository.put(first));
        assertEquals(ClosedLoopPatternRepository.PutResult.FULL, repository.put(second));

        capacity[0] = 0;
        assertNotNull(repository.get(first.patternId()));
        assertEquals(List.of(first), repository.overflowedPatterns());
        assertEquals(List.of(), repository.activePatterns());
        assertEquals(ClosedLoopPatternRepository.PutResult.UPDATED,
                repository.put(first.withSeedMultiplier(8)));
        capacity[0] = 1;
        assertEquals(1, repository.activePatterns().size());
    }

    @Test
    void olderPatternVersionCannotOverwriteNewerStoredDefinition() {
        var repository = new ClosedLoopPatternRepository(() -> 64);
        var id = UUID.randomUUID();
        var newer = payload(id, 4);
        var older = payload(id, 3);

        assertEquals(ClosedLoopPatternRepository.PutResult.ADDED, repository.put(newer));
        assertEquals(ClosedLoopPatternRepository.PutResult.STALE_VERSION, repository.put(older));
        assertEquals(4, repository.get(id).version());
    }

    @Test
    void maintenanceUsesHysteresisPerJobCapAndOneRulePerExactKey() {
        var key = new TestKey("logic_processor");
        var rule = new InventoryMaintenanceRule(
                UUID.randomUUID(), key, 80, 100, 20, true, false, null);

        var starts = InventoryMaintenanceDecision.evaluate(rule, 79, false);
        var staysIdle = InventoryMaintenanceDecision.evaluate(rule, 90, false);
        var continues = InventoryMaintenanceDecision.evaluate(rule.withRuntime(true, null), 90, false);
        var active = InventoryMaintenanceDecision.evaluate(rule.withRuntime(true, UUID.randomUUID()), 70, true);
        var stops = InventoryMaintenanceDecision.evaluate(rule.withRuntime(true, null), 100, false);

        assertEquals(true, starts.replenishing());
        assertEquals(20, starts.requestAmount());
        assertEquals(false, staysIdle.replenishing());
        assertEquals(0, staysIdle.requestAmount());
        assertEquals(10, continues.requestAmount());
        assertEquals(0, active.requestAmount());
        assertEquals(false, stops.replenishing());

        var repository = new InventoryMaintenanceRepository(() -> 1);
        assertEquals(InventoryMaintenanceRepository.PutResult.ADDED, repository.put(rule));
        assertEquals(InventoryMaintenanceRepository.PutResult.UPDATED,
                repository.put(new InventoryMaintenanceRule(
                        UUID.randomUUID(), key, 10, 20, 5, true, false, null)));
        assertEquals(1, repository.size());
        assertEquals(1, repository.activeRules().size());
    }

    @Test
    void maintenanceThresholdEdgesAreStableAndInvalidRangesAreRejected() {
        var key = new TestKey("edge");
        var id = UUID.randomUUID();
        var rule = new InventoryMaintenanceRule(id, key, 10, 20, Long.MAX_VALUE, true, false, null);

        assertEquals(false, InventoryMaintenanceDecision.evaluate(rule, 10, false).replenishing());
        assertEquals(11, InventoryMaintenanceDecision.evaluate(rule, 9, false).requestAmount());
        assertEquals(0, InventoryMaintenanceDecision.evaluate(rule.withRuntime(true, null), 20, false).requestAmount());
        assertEquals(20, InventoryMaintenanceDecision.evaluate(rule, -1, false).requestAmount());
        assertEquals(0, InventoryMaintenanceDecision.evaluate(
                new InventoryMaintenanceRule(id, key, 10, 20, 1, false, true, null), 0, false).requestAmount());

        assertThrows(IllegalArgumentException.class,
                () -> new InventoryMaintenanceRule(id, key, -1, 20, 1, true, false, null));
        assertThrows(IllegalArgumentException.class,
                () -> new InventoryMaintenanceRule(id, key, 20, 20, 1, true, false, null));
        assertThrows(IllegalArgumentException.class,
                () -> new InventoryMaintenanceRule(id, key, 10, 20, 0, true, false, null));
    }

    @Test
    void maintenanceCapacityShrinkKeepsOverflowButOnlyActivatesBackedRules() {
        var capacity = new int[] {2};
        var repository = new InventoryMaintenanceRepository(() -> capacity[0]);
        var first = new InventoryMaintenanceRule(
                UUID.randomUUID(), new TestKey("first_rule"), 10, 20, 5, true, false, null);
        var second = new InventoryMaintenanceRule(
                UUID.randomUUID(), new TestKey("second_rule"), 10, 20, 5, true, false, null);
        assertEquals(InventoryMaintenanceRepository.PutResult.ADDED, repository.put(first));
        assertEquals(InventoryMaintenanceRepository.PutResult.ADDED, repository.put(second));

        capacity[0] = 1;
        assertEquals(List.of(first), repository.activeRules());
        assertNotNull(repository.get(second.key()));
        capacity[0] = 2;
        assertEquals(List.of(first, second), repository.activeRules());
    }

    @Test
    void functionProfileCapacitiesSaturateWithoutOverflow() {
        var profile = new TianshuFunctionProfile(Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, profile.maintenanceRuleCapacity());
        assertEquals(Integer.MAX_VALUE, profile.closedLoopPatternCapacity());
        assertThrows(IllegalArgumentException.class, () -> new TianshuFunctionProfile(-1, 0, 0, 0));
    }

    @Test
    void reservedStockOnlyReducesUsableSnapshotAndNeverCreatesDemand() {
        var key = new TestKey("intermediate_b");
        var reserves = new ReservedStockRepository(() -> 8);

        assertEquals(ReservedStockRepository.PutResult.ADDED, reserves.set(key, 1_000));
        assertEquals(1_000, reserves.usablePreexistingStock(key, 2_000));
        assertEquals(0, reserves.usablePreexistingStock(key, 500));

        assertEquals(ReservedStockRepository.PutResult.UPDATED,
                reserves.set(key, ReservedStockRepository.INFINITE));
        assertEquals(0, reserves.usablePreexistingStock(key, Long.MAX_VALUE));

        assertEquals(ReservedStockRepository.PutResult.REMOVED, reserves.set(key, 0));
        assertEquals(500, reserves.usablePreexistingStock(key, 500));
    }

    @Test
    void reservePlanningUsesOnlyStockAboveFloorThenCraftsTheShortfall() {
        var a = new TestKey("a");
        var b = new TestKey("b");
        var c = new TestKey("c");
        var reserves = new ReservedStockRepository(() -> 8);
        reserves.set(b, 1_000);
        var graph = CraftGraph.<AEKey>builder()
                .pattern(b, 1, List.of(CraftInput.of(a, 2)))
                .pattern(c, 1, List.of(CraftInput.of(b, 2)))
                .stock(b, reserves.usablePreexistingStock(b, 2_000))
                .stock(a, 10_000)
                .build();

        var plan = CraftPlannerV2.plan(graph, c, 1_000);

        assertEquals(true, plan.feasible());
        assertEquals(1_000L, plan.usedStock().get(b));
        assertEquals(2_000L, plan.usedStock().get(a));
        assertEquals(1_000L, plan.firings().entrySet().stream()
                .filter(entry -> entry.getKey().output().equals(b))
                .mapToLong(java.util.Map.Entry::getValue).sum());
    }

    @Test
    void reservePlanningFailsWhenTheProtectedShortfallCannotBeRecrafted() {
        var a = new TestKey("a_missing");
        var b = new TestKey("b_protected");
        var c = new TestKey("c_target");
        var reserves = new ReservedStockRepository(() -> 8);
        reserves.set(b, 1_000);
        var graph = CraftGraph.<AEKey>builder()
                .pattern(b, 1, List.of(CraftInput.of(a, 2)))
                .pattern(c, 1, List.of(CraftInput.of(b, 2)))
                .stock(b, reserves.usablePreexistingStock(b, 2_000))
                .build();

        var plan = CraftPlannerV2.plan(graph, c, 1_000);

        assertEquals(false, plan.feasible());
        assertEquals(1_000L, plan.usedStock().get(b));
        assertEquals(2_000L, plan.missing().get(a));
    }

    @Test
    void reserveFloorDoesNotCreateDemandAndRecipeOverflowRemainsUsable() {
        var a = new TestKey("overflow_a");
        var b = new TestKey("overflow_b");
        var c = new TestKey("overflow_c");
        for (long reserve : List.of(10L, 20L)) {
            var reserves = new ReservedStockRepository(() -> 8);
            reserves.set(a, reserve);
            var graph = CraftGraph.<AEKey>builder()
                    .pattern(a, 10, List.of(CraftInput.of(b, 1)))
                    .pattern(c, 1, List.of(CraftInput.of(a, 1)))
                    .stock(a, reserves.usablePreexistingStock(a, 5))
                    .stock(b, 1)
                    .build();

            var plan = CraftPlannerV2.plan(graph, c, 1);

            assertEquals(true, plan.feasible(), "reserve=" + reserve);
            assertEquals(1L, plan.usedStock().get(b), "reserve=" + reserve);
            assertNull(plan.usedStock().get(a), "protected A must not be consumed");
            assertEquals(1L, plan.firings().entrySet().stream()
                    .filter(entry -> entry.getKey().output().equals(a))
                    .mapToLong(java.util.Map.Entry::getValue).sum());
        }
    }

    @Test
    void infiniteReserveForbidsUsingExistingStockButStillAllowsCraftedFlow() {
        var a = new TestKey("infinite_a");
        var raw = new TestKey("raw");
        var c = new TestKey("infinite_c");
        var reserves = new ReservedStockRepository(() -> 8);
        reserves.set(a, ReservedStockRepository.INFINITE);
        var graph = CraftGraph.<AEKey>builder()
                .pattern(a, 1, List.of(CraftInput.of(raw, 1)))
                .pattern(c, 1, List.of(CraftInput.of(a, 1)))
                .stock(a, reserves.usablePreexistingStock(a, Long.MAX_VALUE))
                .stock(raw, 1)
                .build();

        var plan = CraftPlannerV2.plan(graph, c, 1);

        assertEquals(true, plan.feasible());
        assertNull(plan.usedStock().get(a));
        assertEquals(1L, plan.usedStock().get(raw));
    }

    @Test
    void ignoreSecondaryReserveProtectsOneAggregateAcrossAllVariants() {
        var red = new TestKey("variant_item", "red");
        var blue = new TestKey("variant_item", "blue");
        var reserves = new ReservedStockRepository(() -> 8);
        assertEquals(ReservedStockRepository.PutResult.ADDED,
                reserves.set(red, ReservedStockMatchMode.IGNORE_SECONDARY, 1_000));
        var group = Map.<AEKey, Long>of(red, 600L, blue, 700L);

        long usableRed = reserves.usablePreexistingStock(red, 600, group);
        long usableBlue = reserves.usablePreexistingStock(blue, 700, group);

        assertEquals(300L, usableRed + usableBlue);
        assertEquals(true, reserves.groupsSecondaryVariants(blue));
        assertEquals(1_000L, reserves.reserve(blue));
        assertEquals(ReservedStockMatchMode.IGNORE_SECONDARY, reserves.matchMode(blue));
    }

    @Test
    void fuzzyAndIdOnlyCandidatePlanningCannotExceedAggregateAboveReserve() {
        var red = new TestKey("fuzzy_input", "red");
        var blue = new TestKey("fuzzy_input", "blue");
        var output = new TestKey("fuzzy_output");
        var reserves = new ReservedStockRepository(() -> 8);
        reserves.set(red, ReservedStockMatchMode.IGNORE_SECONDARY, 1_000);
        var group = Map.<AEKey, Long>of(red, 600L, blue, 700L);
        long usableRed = reserves.usablePreexistingStock(red, 600, group);
        long usableBlue = reserves.usablePreexistingStock(blue, 700, group);
        var graph = CraftGraph.<AEKey>builder()
                // These are the two concrete routes produced when a fuzzy/overload ID_ONLY slot
                // is expanded by the AE adapter.
                .pattern(output, 1, List.of(CraftInput.of(red, 1)))
                .pattern(output, 1, List.of(CraftInput.of(blue, 1)))
                .stock(red, usableRed)
                .stock(blue, usableBlue)
                .build();

        var exactCapacity = CraftPlannerV2.plan(graph, output, 300);
        var oneTooMany = CraftPlannerV2.plan(graph, output, 301);

        assertEquals(true, exactCapacity.feasible());
        assertEquals(300L, exactCapacity.usedStock().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(false, oneTooMany.feasible());
        assertEquals(1L, oneTooMany.missing().get(red) != null
                ? oneTooMany.missing().get(red) : oneTooMany.missing().get(blue));
    }

    @Test
    void exactReserveDoesNotProtectSiblingVariants() {
        var red = new TestKey("exact_variant", "red");
        var blue = new TestKey("exact_variant", "blue");
        var reserves = new ReservedStockRepository(() -> 8);
        reserves.set(red, ReservedStockMatchMode.EXACT, 500);

        assertEquals(100L, reserves.usablePreexistingStock(red, 600));
        assertEquals(700L, reserves.usablePreexistingStock(blue, 700));
        assertEquals(false, reserves.groupsSecondaryVariants(blue));
    }

    @Test
    void perRuleReserveAddsToMaintenanceWideReserveInsteadOfReplacingIt() {
        var shared = new TestKey("shared_floor");
        var onlyGlobal = new TestKey("global_floor");
        var global = new ReservedStockRepository(() -> 8);
        var perRule = new ReservedStockRepository(() -> 8);
        global.set(shared, 1_000);
        global.set(onlyGlobal, 400);
        perRule.set(shared, 1_500);
        var policy = new LayeredReservedStockPolicy(global, perRule);

        assertEquals(500L, policy.usablePreexistingStock(shared, 2_000));
        assertEquals(600L, policy.usablePreexistingStock(onlyGlobal, 1_000));
    }

    @Test
    void perRuleReserveCannotRelaxInfiniteMaintenanceWideReserve() {
        var key = new TestKey("globally_infinite");
        var global = new ReservedStockRepository(() -> 8);
        var perRule = new ReservedStockRepository(() -> 8);
        global.set(key, ReservedStockRepository.INFINITE);
        perRule.set(key, 1);

        assertEquals(0L, new LayeredReservedStockPolicy(global, perRule)
                .usablePreexistingStock(key, Long.MAX_VALUE));
    }

    @Test
    void fuzzyGlobalReserveAndExactPerRuleReserveBothConstrainVariants() {
        var red = new TestKey("layered_variant", "red");
        var blue = new TestKey("layered_variant", "blue");
        var global = new ReservedStockRepository(() -> 8);
        var perRule = new ReservedStockRepository(() -> 8);
        global.set(red, ReservedStockMatchMode.IGNORE_SECONDARY, 1_000);
        perRule.set(red, ReservedStockMatchMode.EXACT, 500);
        var policy = new LayeredReservedStockPolicy(global, perRule);
        var group = Map.<AEKey, Long>of(red, 600L, blue, 700L);

        long usableRed = policy.usablePreexistingStock(red, 600, group);
        long usableBlue = policy.usablePreexistingStock(blue, 700, group);

        assertEquals(true, policy.groupsSecondaryVariants(red));
        assertEquals(true, usableRed <= 100L);
        assertEquals(300L, usableRed + usableBlue);
    }

    @Test
    void variantListingMergesStoredAndCraftableVariantsWithSourceState() {
        var red = new TestKey("listed_variant", "red");
        var blue = new TestKey("listed_variant", "blue");
        var green = new TestKey("listed_variant", "green");
        var unrelated = new TestKey("other", "red");
        var stored = new KeyCounter();
        stored.add(red, 5);
        stored.add(blue, 0);
        stored.add(unrelated, 99);

        var variants = MaintenanceVariantService.list(
                stored, List.of(blue, green, unrelated), red);

        assertEquals(3, variants.size());
        assertEquals(5L, variants.stream().filter(entry -> entry.key().equals(red))
                .findFirst().orElseThrow().storedAmount());
        assertEquals(true, variants.stream().filter(entry -> entry.key().equals(blue))
                .findFirst().orElseThrow().craftable());
        assertEquals(true, variants.stream().filter(entry -> entry.key().equals(green))
                .findFirst().orElseThrow().craftable());
    }

    @Test
    void maintenanceBadgeUsesOnlyTheFourRequestedStates() {
        assertEquals(InventoryMaintenanceBadge.GREEN,
                InventoryMaintenanceBadge.from(InventoryMaintenanceStatus.SATISFIED));
        assertEquals(InventoryMaintenanceBadge.YELLOW,
                InventoryMaintenanceBadge.from(InventoryMaintenanceStatus.CRAFTING));
        assertEquals(InventoryMaintenanceBadge.RED,
                InventoryMaintenanceBadge.from(InventoryMaintenanceStatus.MISSING_INGREDIENTS));
        assertEquals(InventoryMaintenanceBadge.GRAY,
                InventoryMaintenanceBadge.from(InventoryMaintenanceStatus.DISABLED));
    }

    @Test
    void reserveTopologyIsTargetFirstAndDeduplicatesSharedDependencies() {
        var target = new TestKey("target");
        var middleA = new TestKey("middle_a");
        var middleB = new TestKey("middle_b");
        var raw = new TestKey("raw");
        var patterns = java.util.Map.<AEKey, List<IPatternDetails>>of(
                target, List.of(pattern(target, middleA, middleB)),
                middleA, List.of(pattern(middleA, raw)),
                middleB, List.of(pattern(middleB, raw)));

        var topology = MaintenanceTopologyService.build(
                key -> patterns.getOrDefault(key, List.of()), target);

        assertEquals(List.of(target, middleA, middleB, raw),
                topology.stream().map(MaintenanceTopologyService.Entry::key).toList());
        assertEquals(List.of(0, 1, 1, 2),
                topology.stream().map(MaintenanceTopologyService.Entry::depth).toList());
    }

    private static IPatternDetails pattern(AEKey output, AEKey... inputs) {
        var patternInputs = new IPatternDetails.IInput[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            var key = inputs[i];
            patternInputs[i] = new IPatternDetails.IInput() {
                @Override public GenericStack[] getPossibleInputs() {
                    return new GenericStack[] {new GenericStack(key, 1)};
                }
                @Override public long getMultiplier() { return 1; }
                @Override public boolean isValid(AEKey candidate, Level level) {
                    return key.equals(candidate);
                }
                @Override public AEKey getRemainingKey(AEKey template) { return null; }
            };
        }
        return new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return null; }
            @Override public IInput[] getInputs() { return patternInputs; }
            @Override public List<GenericStack> getOutputs() {
                return List.of(new GenericStack(output, 1));
            }
        };
    }

    private static ClosedLoopPatternPayload payload(UUID id, long version) {
        var member = new SourcePatternSnapshot(
                ResourceLocation.fromNamespaceAndPath("ae2", "encoded_processing_pattern"), null, null);
        return new ClosedLoopPatternPayload(
                id, version, List.of(new ClosedLoopMemberPattern(member, 1)),
                List.of(new GenericStack(new TestKey("template"), 1)),
                List.of(new GenericStack(new TestKey("diamond"), 7)),
                List.of(new GenericStack(new TestKey("template"), 1)),
                1, true);
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
