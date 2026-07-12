package com.moakiee.ae2lt.logic.tianshu;

public enum CpuMainCoreTier {
    BASELINE(1L, 1),
    QUANTUM(16L, 2),
    OVERLOAD(256L, 4),
    MULTIDIMENSIONAL(Long.MAX_VALUE, 8);

    private final long storageMultiplier;
    private final int parallelMultiplier;

    CpuMainCoreTier(long storageMultiplier, int parallelMultiplier) {
        this.storageMultiplier = storageMultiplier;
        this.parallelMultiplier = parallelMultiplier;
    }

    public long storageMultiplier() {
        return storageMultiplier;
    }

    public int parallelMultiplier() {
        return parallelMultiplier;
    }
}
