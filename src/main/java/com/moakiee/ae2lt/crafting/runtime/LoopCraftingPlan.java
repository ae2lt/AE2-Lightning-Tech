package com.moakiee.ae2lt.crafting.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;

import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPoolHost;
import com.moakiee.ae2lt.crafting.timewheel.ReusableSeedPattern;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelPoolRestrictedPattern;
import com.moakiee.thunderbolt.core.crafting.planner.ReusableBootstrapRoute;
import com.moakiee.thunderbolt.core.crafting.planner.ReusableStockUsageKey;

/**
 * A closed-loop plan produced from an AE2 {@link CraftingPlan} and restricted to one compatible
 * time-wheel CPU pool.
 */
public record LoopCraftingPlan(
        CraftingPlan delegate,
        List<TimeWheelPoolRestrictedPattern> restrictions,
        Map<AEKey, Long> totalReusableSeeds,
        Map<AEKey, Long> hostReusableSeeds,
        List<HostReusableSeedAllocation> hostReusableSeedAllocations) implements ICraftingPlan {

    public LoopCraftingPlan {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        restrictions = List.copyOf(restrictions);
        totalReusableSeeds = Map.copyOf(totalReusableSeeds);
        hostReusableSeeds = Map.copyOf(hostReusableSeeds);
        hostReusableSeedAllocations = List.copyOf(hostReusableSeedAllocations);
        if (restrictions.isEmpty()) {
            throw new IllegalArgumentException("loop plan must have at least one restriction");
        }
    }

    /** Wraps an AE2 plan only when it contains a time-wheel-restricted pattern. */
    public static ICraftingPlan wrapIfNeeded(ICraftingPlan plan) {
        return wrapIfNeeded(plan, null);
    }

    /** Wraps a fast plan and carries the exact host-private stock actually borrowed by the planner. */
    public static ICraftingPlan wrapIfNeeded(
            ICraftingPlan plan,
            Map<ReusableStockUsageKey<AEKey>, Long> usedReusableStock) {
        if (!(plan instanceof CraftingPlan craftingPlan)) {
            return plan;
        }
        var restrictions = new ArrayList<TimeWheelPoolRestrictedPattern>();
        var reusablePatterns = new ArrayList<ReusableSeedPattern>();
        for (var details : craftingPlan.patternTimes().keySet()) {
            if (details instanceof TimeWheelPoolRestrictedPattern restricted) {
                restrictions.add(restricted);
            }
            if (details instanceof ReusableSeedPattern seeded) {
                reusablePatterns.add(seeded);
            }
        }
        var totalSeeds = aggregateTotalSeeds(reusablePatterns);
        var hostSeeds = new LinkedHashMap<AEKey, Long>();
        var hostAllocations = new ArrayList<HostReusableSeedAllocation>();
        var hostUsageByRoute = new LinkedHashMap<HostRequirementKey, Long>();
        var hostLimitByRoute = aggregateHostLimits(reusablePatterns);
        if (usedReusableStock != null) {
            for (var entry : usedReusableStock.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                    var bootstrapRoute = entry.getKey().routingScope()
                            instanceof ReusableBootstrapRoute<?> route ? route : null;
                    var owner = bootstrapRoute == null
                            ? reusableStockOwner(reusablePatterns, entry.getKey())
                            : reusableBootstrapOwner(reusablePatterns, entry.getKey(), bootstrapRoute);
                    if (owner == null) {
                        throw new IllegalStateException(
                                "private reusable-stock usage has no owning loop pattern");
                    }
                    AEKey plannedKey = bootstrapRoute != null
                            && bootstrapRoute.returnedSeedKey() instanceof AEKey key
                            ? key
                            : entry.getKey().key();
                    hostSeeds.merge(
                            bootstrapRoute != null
                                    ? entry.getKey().actualKey() : entry.getKey().key(),
                            entry.getValue(),
                            LoopCraftingPlan::saturatingAdd);
                    if (bootstrapRoute == null) {
                        hostUsageByRoute.merge(
                                HostRequirementKey.from(entry.getKey()), entry.getValue(),
                                LoopCraftingPlan::saturatingAdd);
                    }
                    hostAllocations.add(new HostReusableSeedAllocation(
                            entry.getKey().storageScope(),
                            entry.getKey().poolScope(),
                            owner.reusableStockSource().routingScope(),
                            plannedKey,
                            entry.getKey().actualKey(),
                            entry.getValue(),
                            owner.reusableSeedGroupId(),
                            owner.hasSingleSeedInputPerMember(),
                            bootstrapRoute != null));
                }
            }
        }
        for (var usage : hostUsageByRoute.entrySet()) {
            if (usage.getValue() > hostLimitByRoute.getOrDefault(usage.getKey(), 0L)) {
                throw new IllegalStateException(
                        "private reusable-stock usage exceeds its route seed requirement");
            }
        }
        return restrictions.isEmpty()
                ? craftingPlan
                : new LoopCraftingPlan(
                        craftingPlan,
                        restrictions,
                        totalSeeds,
                        hostSeeds,
                        hostAllocations);
    }

    /**
     * Returns the concrete AE2 plan used by display-only integrations while preserving the wrapper
     * everywhere else, especially in the confirmation menu's stored result and CPU submission.
     */
    public static ICraftingPlan unwrapForSummary(ICraftingPlan plan) {
        return plan instanceof LoopCraftingPlan loopPlan ? loopPlan.delegate() : plan;
    }

    public boolean canRunOn(TimeWheelCraftingCpuPoolHost host) {
        for (var restriction : restrictions) {
            if (!restriction.acceptsTimeWheelPool(host)) {
                return false;
            }
        }
        return true;
    }

    public boolean acceptsReusableSeedVariant(AEKey planned, AEKey actual) {
        if (planned == null || actual == null) return false;
        for (var details : delegate.patternTimes().keySet()) {
            if (details instanceof ReusableSeedPattern seeded
                    && seeded.totalReusableSeedRequirements().getOrDefault(planned, 0L) > 0
                    && seeded.acceptsReusableSeedVariant(planned, actual)) {
                return true;
            }
        }
        return planned.equals(actual);
    }

    /** Variant matching narrowed to the exact logical pool that borrowed this host seed. */
    public boolean acceptsReusableSeedVariant(
            HostReusableSeedAllocation allocation, AEKey actual) {
        if (allocation == null || actual == null) return false;
        if (allocation.bootstrap()) {
            return allocation.actualKey().equals(actual);
        }
        for (var details : delegate.patternTimes().keySet()) {
            if (!(details instanceof ReusableSeedPattern seeded)) continue;
            var source = seeded.reusableStockSource();
            if (allocation.storageScope().equals(source.storageScope())
                    && allocation.poolScope().equals(source.poolScope())
                    && allocation.routingScope().equals(source.routingScope())
                    && seeded.totalReusableSeedRequirements()
                            .getOrDefault(allocation.plannedKey(), 0L) > 0
                    && allocation.actualKey().equals(actual)
                    && seeded.acceptsReusableSeedVariant(allocation.plannedKey(), actual)) {
                return true;
            }
        }
        return false;
    }

    /** Reusable-seed requirements kept separate by contracted cycle. */
    public Map<UUID, Map<AEKey, Long>> reusableSeedGroups() {
        var groups = new LinkedHashMap<UUID, Map<AEKey, Long>>();
        for (var details : delegate.patternTimes().keySet()) {
            if (!(details instanceof ReusableSeedPattern seeded)) continue;
            groups.merge(
                    seeded.reusableSeedGroupId(),
                    positiveCopy(seeded.totalReusableSeedRequirements()),
                    LoopCraftingPlan::mergePositiveMaxCopies);
        }
        return Map.copyOf(groups);
    }

    /** Strongly-connected state keys kept separate for each contracted cycle. */
    public Map<UUID, Set<AEKey>> reusableSeedCycleKeys() {
        var groups = new LinkedHashMap<UUID, Set<AEKey>>();
        for (var details : delegate.patternTimes().keySet()) {
            if (!(details instanceof ReusableSeedPattern seeded)) continue;
            groups.merge(
                    seeded.reusableSeedGroupId(),
                    Set.copyOf(seeded.reusableSeedCycleKeys()),
                    (left, right) -> {
                        var merged = new java.util.LinkedHashSet<AEKey>(left);
                        merged.addAll(right);
                        return Set.copyOf(merged);
                    });
        }
        return Map.copyOf(groups);
    }

    /** Groups that must not borrow the protected phase state of another loop macro. */
    public Set<UUID> dedicatedReusableSeedGroups() {
        var groups = new java.util.LinkedHashSet<UUID>();
        for (var details : delegate.patternTimes().keySet()) {
            if (details instanceof ReusableSeedPattern seeded
                    && !seeded.hasSingleSeedInputPerMember()) {
                groups.add(seeded.reusableSeedGroupId());
            }
        }
        return Set.copyOf(groups);
    }

    private static void mergePositiveSum(Map<AEKey, Long> target, Map<AEKey, Long> source) {
        for (var entry : source.entrySet()) {
            if (entry.getKey() != null && positive(entry.getValue()) > 0) {
                target.merge(entry.getKey(), entry.getValue(), LoopCraftingPlan::saturatingAdd);
            }
        }
    }

    private static Map<AEKey, Long> positiveCopy(Map<AEKey, Long> source) {
        var result = new LinkedHashMap<AEKey, Long>();
        mergePositiveSum(result, source);
        return Map.copyOf(result);
    }

    private static Map<AEKey, Long> mergePositiveMaxCopies(
            Map<AEKey, Long> left, Map<AEKey, Long> right) {
        var result = new LinkedHashMap<AEKey, Long>(left);
        for (var entry : right.entrySet()) {
            if (entry.getKey() != null && positive(entry.getValue()) > 0) {
                result.merge(entry.getKey(), entry.getValue(), Math::max);
            }
        }
        return Map.copyOf(result);
    }

    private static Map<AEKey, Long> aggregateTotalSeeds(
            List<ReusableSeedPattern> patterns) {
        var sharedByStorage = new LinkedHashMap<Object, Map<AEKey, Long>>();
        var total = new LinkedHashMap<AEKey, Long>();
        for (var seeded : patterns) {
            var requirements = positiveCopy(seeded.totalReusableSeedRequirements());
            if (seeded.hasSingleSeedInputPerMember()) {
                sharedByStorage.merge(
                        seeded.reusableSeedStorageScope(), requirements,
                        LoopCraftingPlan::mergePositiveMaxCopies);
            } else {
                mergePositiveSum(total, requirements);
            }
        }
        for (var shared : sharedByStorage.values()) {
            mergePositiveSum(total, shared);
        }
        return Map.copyOf(total);
    }

    /**
     * Physical host borrowing is bounded per route, not by the final shared return quota. Several
     * patterns may publish the same group view, so duplicate route requirements merge by maximum.
     */
    private static Map<HostRequirementKey, Long> aggregateHostLimits(
            List<ReusableSeedPattern> patterns) {
        var result = new LinkedHashMap<HostRequirementKey, Long>();
        for (var seeded : patterns) {
            var source = seeded.reusableStockSource();
            for (var requirement : seeded.totalReusableSeedRequirements().entrySet()) {
                long amount = positive(requirement.getValue());
                if (requirement.getKey() != null && amount > 0) {
                    result.merge(new HostRequirementKey(
                                    source.storageScope(), source.poolScope(), source.routingScope(),
                                    requirement.getKey()),
                            amount, Math::max);
                }
            }
        }
        return Map.copyOf(result);
    }

    private static ReusableSeedPattern reusableStockOwner(
            List<ReusableSeedPattern> patterns,
            ReusableStockUsageKey<AEKey> usage) {
        for (var seeded : patterns) {
            var source = seeded.reusableStockSource();
            if (usage.storageScope().equals(source.storageScope())
                    && usage.poolScope().equals(source.poolScope())
                    && usage.routingScope().equals(source.routingScope())
                    && seeded.totalReusableSeedRequirements()
                            .getOrDefault(usage.key(), 0L) > 0) {
                return seeded;
            }
        }
        return null;
    }

    private static ReusableSeedPattern reusableBootstrapOwner(
            List<ReusableSeedPattern> patterns,
            ReusableStockUsageKey<AEKey> usage,
            ReusableBootstrapRoute<?> bootstrapRoute) {
        if (!(bootstrapRoute.returnedSeedKey() instanceof AEKey returnedSeedKey)) {
            return null;
        }
        for (var seeded : patterns) {
            var source = seeded.reusableStockSource();
            if (usage.storageScope().equals(source.storageScope())
                    && usage.poolScope().equals(source.poolScope())
                    && bootstrapRoute.ownerRoutingScope().equals(source.routingScope())
                    && seeded.totalReusableSeedRequirements()
                            .getOrDefault(returnedSeedKey, 0L) > 0) {
                return seeded;
            }
        }
        return null;
    }

    public record HostReusableSeedAllocation(
            Object storageScope,
            Object poolScope,
            Object routingScope,
            AEKey plannedKey,
            AEKey actualKey,
            long amount,
            UUID reusableSeedGroupId,
            boolean sharedPool,
            boolean bootstrap) {
        public HostReusableSeedAllocation {
            Objects.requireNonNull(storageScope, "storageScope");
            Objects.requireNonNull(poolScope, "poolScope");
            Objects.requireNonNull(routingScope, "routingScope");
            Objects.requireNonNull(plannedKey, "plannedKey");
            Objects.requireNonNull(actualKey, "actualKey");
            Objects.requireNonNull(reusableSeedGroupId, "reusableSeedGroupId");
            if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        }
    }

    private record HostRequirementKey(
            Object storageScope, Object poolScope, Object routingScope, AEKey plannedKey) {
        private static HostRequirementKey from(ReusableStockUsageKey<AEKey> usage) {
            return new HostRequirementKey(
                    usage.storageScope(), usage.poolScope(), usage.routingScope(), usage.key());
        }
    }

    private static long positive(Long value) {
        return value != null ? Math.max(0L, value) : 0L;
    }

    private static long saturatingAdd(long left, long right) {
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    @Override
    public GenericStack finalOutput() {
        return delegate.finalOutput();
    }

    @Override
    public long bytes() {
        return delegate.bytes();
    }

    @Override
    public boolean simulation() {
        return delegate.simulation();
    }

    @Override
    public boolean multiplePaths() {
        return delegate.multiplePaths();
    }

    @Override
    public KeyCounter usedItems() {
        return delegate.usedItems();
    }

    @Override
    public KeyCounter emittedItems() {
        return delegate.emittedItems();
    }

    @Override
    public KeyCounter missingItems() {
        return delegate.missingItems();
    }

    @Override
    public Map<IPatternDetails, Long> patternTimes() {
        return delegate.patternTimes();
    }

}
