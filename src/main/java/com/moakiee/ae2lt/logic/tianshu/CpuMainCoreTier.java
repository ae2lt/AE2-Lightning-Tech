package com.moakiee.ae2lt.logic.tianshu;

import com.moakiee.ae2lt.logic.compute.ComputeTier;

public enum CpuMainCoreTier {
    BASELINE(ComputeTier.BASELINE, 256.0D),
    QUANTUM(ComputeTier.QUANTUM, 8_192.0D),
    OVERLOAD(ComputeTier.OVERLOAD, 262_144.0D),
    // Creative/development tier: retain the port's legacy link cost.
    MULTIDIMENSIONAL(ComputeTier.MULTIDIMENSIONAL, 8.0D);

    private final ComputeTier computeTier;
    private final double idlePowerUsage;

    CpuMainCoreTier(ComputeTier computeTier, double idlePowerUsage) {
        this.computeTier = computeTier;
        this.idlePowerUsage = idlePowerUsage;
    }

    public ComputeTier computeTier() {
        return computeTier;
    }

    public double idlePowerUsage() {
        return idlePowerUsage;
    }
}
