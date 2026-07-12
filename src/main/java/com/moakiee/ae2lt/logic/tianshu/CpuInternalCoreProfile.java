package com.moakiee.ae2lt.logic.tianshu;

public record CpuInternalCoreProfile(
        CpuMainCoreTier mainCore,
        int capacityCoreCount,
        int parallelCoreCount,
        long storageBytes,
        int parallelism,
        boolean parallelCapped) {
    public static CpuInternalCoreProfile empty() {
        return new CpuInternalCoreProfile(null, 0, 0, 0L, 0, false);
    }
}
