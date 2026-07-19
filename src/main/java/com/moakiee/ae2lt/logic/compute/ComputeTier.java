package com.moakiee.ae2lt.logic.compute;

public enum ComputeTier {
    BASELINE(0, 512, 1L << 20, 1_024L, false),
    QUANTUM(15, 3_072, 256L << 20, 10_240L, false),
    OVERLOAD(15, 16_384, 64L << 30, 4_194_304L, false),
    MULTIDIMENSIONAL(0, 16_384, Long.MAX_VALUE, Long.MAX_VALUE, true);

    private final int maxAmplifierUnits;
    private final int dispatchCap;
    private final long internalStorage;
    private final long copyCap;
    private final boolean multidimensional;

    ComputeTier(int maxAmplifierUnits, int dispatchCap, long internalStorage, long copyCap,
                boolean multidimensional) {
        this.maxAmplifierUnits = maxAmplifierUnits;
        this.dispatchCap = dispatchCap;
        this.internalStorage = internalStorage;
        this.copyCap = copyCap;
        this.multidimensional = multidimensional;
    }

    public int maxAmplifierUnits() {
        return maxAmplifierUnits;
    }

    public int dispatchCap() {
        return dispatchCap;
    }

    public long internalStorage() {
        return internalStorage;
    }

    public long copyCap() {
        return copyCap;
    }

    public boolean multidimensional() {
        return multidimensional;
    }
}
