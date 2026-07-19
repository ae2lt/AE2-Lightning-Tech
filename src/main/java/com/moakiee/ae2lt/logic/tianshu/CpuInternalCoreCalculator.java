package com.moakiee.ae2lt.logic.tianshu;

public final class CpuInternalCoreCalculator {
    public static final long STORAGE_PER_CORE = com.moakiee.ae2lt.logic.compute.UnifiedCraftingComputeCalculator.STORAGE_PER_UNIT;
    public static final int PARALLEL_PER_CORE = com.moakiee.ae2lt.logic.compute.UnifiedCraftingComputeCalculator.DISPATCH_PER_UNIT;
    public static final int PARALLEL_CAP = 16_384;

    public static CpuInternalCoreProfile calculate(
            CpuMainCoreTier mainCore,
            int capacityCoreCount,
            int parallelCoreCount) {
        return calculate(mainCore, capacityCoreCount, parallelCoreCount, 0);
    }

    public static CpuInternalCoreProfile calculate(
            CpuMainCoreTier mainCore,
            int capacityCoreCount,
            int parallelCoreCount,
            int amplifierCoreCount) {
        if (mainCore == null) throw new IllegalArgumentException("Main core is required");
        if (capacityCoreCount < 0 || parallelCoreCount < 0 || amplifierCoreCount < 0
                || capacityCoreCount + parallelCoreCount + amplifierCoreCount > 26) {
            throw new IllegalArgumentException("Peripheral core count must be between 0 and 26");
        }
        var units = new com.moakiee.ae2lt.logic.compute.ComputingUnitTotals(
                parallelCoreCount, amplifierCoreCount, capacityCoreCount, 0);
        var envelope = com.moakiee.ae2lt.logic.compute.UnifiedCraftingComputeCalculator
                .cpuEnvelope(mainCore.computeTier(), units);
        return new CpuInternalCoreProfile(
                mainCore,
                capacityCoreCount,
                parallelCoreCount,
                amplifierCoreCount,
                envelope.storageBytes(),
                envelope.successfulDispatchesPerTick(),
                envelope.maxCopiesPerTick(),
                envelope.unboundedBatch(),
                envelope.dispatchCapped());
    }

    static long saturatingMultiply(long left, long right) {
        return com.moakiee.ae2lt.logic.compute.UnifiedCraftingComputeCalculator
                .saturatedMultiply(left, right);
    }

    private CpuInternalCoreCalculator() {
    }
}
