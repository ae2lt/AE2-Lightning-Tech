package com.moakiee.ae2lt.logic.craft;

public enum MatrixPatternStorageTier {
    CUSTOM(0),
    T1(MatrixPatternStorageUnit.T1_CAPACITY),
    T2(MatrixPatternStorageUnit.T2_CAPACITY);

    private final int capacity;

    MatrixPatternStorageTier(int capacity) {
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }
}
