package com.moakiee.ae2lt.logic.tianshu;

public final class CpuInternalCoreCalculator {
    public static final long STORAGE_PER_UNIT = com.moakiee.ae2lt.logic.compute.UnifiedCraftingComputeCalculator.STORAGE_PER_UNIT;
    public static final int PARALLEL_PER_UNIT = com.moakiee.ae2lt.logic.compute.UnifiedCraftingComputeCalculator.DISPATCH_PER_UNIT;
    public static final int PARALLEL_CAP = 16_384;

    public static CpuInternalCoreProfile calculate(
            CpuMainCoreTier mainCore,
            int storageUnitCount,
            int parallelUnitCount) {
        return calculate(mainCore, storageUnitCount, parallelUnitCount, 0);
    }

    public static CpuInternalCoreProfile calculate(
            CpuMainCoreTier mainCore,
            int storageUnitCount,
            int parallelUnitCount,
            int amplifierUnitCount) {
        if (mainCore == null) throw new IllegalArgumentException("Main core is required");
        if (storageUnitCount < 0 || parallelUnitCount < 0 || amplifierUnitCount < 0
                || storageUnitCount + parallelUnitCount + amplifierUnitCount > 26) {
            throw new IllegalArgumentException("Peripheral unit count must be between 0 and 26");
        }
        var units = new com.moakiee.ae2lt.logic.compute.ComputingUnitTotals(
                parallelUnitCount, amplifierUnitCount, storageUnitCount, 0);
        var envelope = com.moakiee.ae2lt.logic.compute.UnifiedCraftingComputeCalculator
                .cpuEnvelope(mainCore.computeTier(), units);
        return new CpuInternalCoreProfile(
                mainCore,
                storageUnitCount,
                parallelUnitCount,
                amplifierUnitCount,
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
