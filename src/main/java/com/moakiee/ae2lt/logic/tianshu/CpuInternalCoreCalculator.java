package com.moakiee.ae2lt.logic.tianshu;

public final class CpuInternalCoreCalculator {
    public static final long STORAGE_PER_CORE = 64L * 1024L * 1024L;
    public static final int PARALLEL_PER_CORE = 128;
    public static final int PARALLEL_CAP = 16_384;

    public static CpuInternalCoreProfile calculate(
            CpuMainCoreTier mainCore,
            int capacityCoreCount,
            int parallelCoreCount) {
        if (mainCore == null) throw new IllegalArgumentException("Main core is required");
        if (capacityCoreCount < 0 || parallelCoreCount < 0 || capacityCoreCount + parallelCoreCount > 26) {
            throw new IllegalArgumentException("Peripheral core count must be between 0 and 26");
        }
        long baseStorage = STORAGE_PER_CORE * capacityCoreCount;
        long storage = mainCore == CpuMainCoreTier.MULTIDIMENSIONAL
                ? Long.MAX_VALUE : saturatingMultiply(baseStorage, mainCore.storageMultiplier());
        long uncappedParallel = (long) parallelCoreCount * PARALLEL_PER_CORE * mainCore.parallelMultiplier();
        int parallel = (int) Math.min(uncappedParallel, PARALLEL_CAP);
        return new CpuInternalCoreProfile(
                mainCore, capacityCoreCount, parallelCoreCount, storage, parallel,
                uncappedParallel > PARALLEL_CAP);
    }

    static long saturatingMultiply(long left, long right) {
        if (left == 0L || right == 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private CpuInternalCoreCalculator() {
    }
}
