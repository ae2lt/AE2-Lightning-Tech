package com.moakiee.ae2lt.logic.tianshu;

import com.moakiee.ae2lt.logic.compute.ComputeTier;

public enum CpuMainCoreTier {
    BASELINE(ComputeTier.BASELINE),
    QUANTUM(ComputeTier.QUANTUM),
    OVERLOAD(ComputeTier.OVERLOAD),
    MULTIDIMENSIONAL(ComputeTier.MULTIDIMENSIONAL);

    private final ComputeTier computeTier;

    CpuMainCoreTier(ComputeTier computeTier) {
        this.computeTier = computeTier;
    }

    public ComputeTier computeTier() {
        return computeTier;
    }
}
