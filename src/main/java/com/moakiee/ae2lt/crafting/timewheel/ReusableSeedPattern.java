package com.moakiee.ae2lt.crafting.timewheel;

import appeng.api.stacks.AEKey;
import com.moakiee.thunderbolt.core.crafting.planner.ReusableStockSource;
import com.moakiee.thunderbolt.core.crafting.support.ReusableStockPattern;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Planning macro that requires a reusable seed pool once per submitted job, not once per firing. */
public interface ReusableSeedPattern extends ReusableStockPattern {
    /** Stable identity of the physical host storage that owns this pattern's private seeds. */
    Object reusableSeedStorageScope();

    /** Stable identity used to keep this contracted cycle's reserve separate from other cycles. */
    UUID reusableSeedGroupId();

    /** Keys in the strongly-connected component that can carry this cycle's reusable state. */
    Set<AEKey> reusableSeedCycleKeys();

    /** True only when every expanded child has exactly one positive {@code inputSeed} key. */
    boolean hasSingleSeedInputPerMember();

    /** Total seed pool required by this job, including its configured parallel multiplier. */
    Map<AEKey, Long> totalReusableSeedRequirements();

    /** Whether an actual stored variant can satisfy the planned reusable-seed key. */
    default boolean acceptsReusableSeedVariant(AEKey planned, AEKey actual) {
        return planned != null && planned.equals(actual);
    }

    /** Planner routing: safe single-seed loops share one pool; multi-seed loops remain isolated. */
    default ReusableStockSource reusableStockSource() {
        Object storage = Objects.requireNonNull(
                reusableSeedStorageScope(), "reusableSeedStorageScope");
        Object pool = hasSingleSeedInputPerMember()
                ? new SharedPool(storage)
                : new DedicatedPool(storage, reusableSeedGroupId());
        return new ReusableStockSource(storage, pool, reusableSeedGroupId());
    }

    @Override
    default Map<AEKey, Long> reusableStockRequirements() {
        return totalReusableSeedRequirements();
    }

    @Override
    default boolean acceptsReusableStockVariant(AEKey planned, AEKey actual) {
        return acceptsReusableSeedVariant(planned, actual);
    }

    @Override
    default Map<AEKey, Long> availableReusableStockSnapshot() {
        return availableReusableSeedSnapshot();
    }

    /**
     * Read-only host seed snapshot available to this contracted pattern during planning.
     *
     * <p>Each map key is a concrete physical {@code actual} variant, never a planned requirement
     * key with fuzzy-compatible variants pre-aggregated into it. Multiple patterns sharing one
     * {@link #reusableSeedStorageScope() storage scope} may report the same physical snapshot;
     * {@code CraftGraph.Builder} de-duplicates those repeated observations by taking the maximum
     * quantity for each {@code (storageScope, actualVariant)} pair. The executing CPU still
     * performs the real extraction atomically when the job is submitted.
     */
    default Map<AEKey, Long> availableReusableSeedSnapshot() {
        return Map.of();
    }

    record SharedPool(Object storageScope) {
        public SharedPool {
            Objects.requireNonNull(storageScope, "storageScope");
        }
    }

    record DedicatedPool(Object storageScope, UUID groupId) {
        public DedicatedPool {
            Objects.requireNonNull(storageScope, "storageScope");
            Objects.requireNonNull(groupId, "groupId");
        }
    }
}
