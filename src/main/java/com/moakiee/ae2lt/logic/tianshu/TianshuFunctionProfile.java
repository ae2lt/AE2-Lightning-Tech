package com.moakiee.ae2lt.logic.tianshu;

/** Functional storage capacity contributed by the physical peripheral units in a formed Tianshu. */
public record TianshuFunctionProfile(
        int closedLoopPatternStorageCount,
        int closedLoopSeedStorageCount) {
    public static final int PATTERNS_PER_CLOSED_LOOP_STORAGE = 64;

    public TianshuFunctionProfile {
        if (closedLoopPatternStorageCount < 0
                || closedLoopSeedStorageCount < 0) {
            throw new IllegalArgumentException("Tianshu function unit counts cannot be negative");
        }
    }

    public static TianshuFunctionProfile empty() {
        return new TianshuFunctionProfile(0, 0);
    }

    /** Inventory maintenance is built into every valid main supercomputing unit. */
    public boolean supportsInventoryMaintenance() {
        return true;
    }

    /** Closed-loop planning is built into every valid main supercomputing unit. */
    public boolean supportsClosedLoopPatterns() {
        return true;
    }

    public boolean supportsClosedLoopSeeds() {
        return closedLoopSeedStorageCount > 0;
    }

    public int maintenanceRuleCapacity() {
        return com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceLimits.MAX_ENTRIES;
    }

    public int closedLoopPatternCapacity() {
        return saturatingMultiply(closedLoopPatternStorageCount, PATTERNS_PER_CLOSED_LOOP_STORAGE);
    }

    private static int saturatingMultiply(int count, int perUnit) {
        long result = (long) count * perUnit;
        return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }
}
