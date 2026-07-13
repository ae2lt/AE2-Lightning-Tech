package com.moakiee.ae2lt.logic.tianshu;

/**
 * Functional capacity contributed by the optional peripheral units in a formed Tianshu.
 * Data is owned by the port so breaking/reforming the structure does not discard it.
 */
public record TianshuFunctionProfile(
        int inventoryMaintenanceCoreCount,
        int closedLoopPatternCoreCount,
        int closedLoopPatternStorageCount,
        int closedLoopSeedStorageCount) {
    public static final int RULES_PER_MAINTENANCE_CORE = 64;
    public static final int PATTERNS_PER_CLOSED_LOOP_STORAGE = 64;

    public TianshuFunctionProfile {
        if (inventoryMaintenanceCoreCount < 0
                || closedLoopPatternCoreCount < 0
                || closedLoopPatternStorageCount < 0
                || closedLoopSeedStorageCount < 0) {
            throw new IllegalArgumentException("Tianshu function unit counts cannot be negative");
        }
    }

    public static TianshuFunctionProfile empty() {
        return new TianshuFunctionProfile(0, 0, 0, 0);
    }

    public boolean supportsInventoryMaintenance() {
        return inventoryMaintenanceCoreCount > 0;
    }

    public boolean supportsClosedLoopPatterns() {
        return closedLoopPatternCoreCount > 0;
    }

    public boolean supportsClosedLoopSeeds() {
        return closedLoopSeedStorageCount > 0;
    }

    public int maintenanceRuleCapacity() {
        return saturatingMultiply(inventoryMaintenanceCoreCount, RULES_PER_MAINTENANCE_CORE);
    }

    public int closedLoopPatternCapacity() {
        return supportsClosedLoopPatterns()
                ? saturatingMultiply(closedLoopPatternStorageCount, PATTERNS_PER_CLOSED_LOOP_STORAGE)
                : 0;
    }

    private static int saturatingMultiply(int count, int perUnit) {
        long result = (long) count * perUnit;
        return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }
}
