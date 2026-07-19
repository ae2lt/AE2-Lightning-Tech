package com.moakiee.ae2lt.logic.tianshu;

public record CpuInternalCoreProfile(
        CpuMainCoreTier mainCore,
        int capacityCoreCount,
        int parallelCoreCount,
        int amplifierCoreCount,
        long storageBytes,
        int successfulDispatchesPerTick,
        long maxCopiesPerTick,
        boolean unboundedBatch,
        boolean parallelCapped) {
    public static CpuInternalCoreProfile empty() {
        return new CpuInternalCoreProfile(null, 0, 0, 0, 0L, 0, 0L, false, false);
    }

    /** Compatibility name used by existing controller/menu code. */
    public int parallelism() {
        return successfulDispatchesPerTick;
    }

    /** AE2 exposes co-processors separately from the one built-in operation. */
    public int coProcessors() {
        return Math.max(0, successfulDispatchesPerTick - 1);
    }
}
