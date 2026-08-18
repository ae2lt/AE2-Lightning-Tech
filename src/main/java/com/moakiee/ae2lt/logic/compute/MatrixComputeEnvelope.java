package com.moakiee.ae2lt.logic.compute;

public record MatrixComputeEnvelope(
        long operationsPerTick,
        int maxProviderCallsPerTick,
        double thermalEfficiency,
        boolean unboundedOperations) {
    public static final int MAX_PROVIDER_CALLS_PER_TICK = 16_384;

    public MatrixComputeEnvelope {
        operationsPerTick = Math.max(0L, operationsPerTick);
        maxProviderCallsPerTick = Math.max(0,
                Math.min(maxProviderCallsPerTick, MAX_PROVIDER_CALLS_PER_TICK));
        thermalEfficiency = Double.isFinite(thermalEfficiency)
                ? Math.max(0.0D, thermalEfficiency) : 0.0D;
    }
}
