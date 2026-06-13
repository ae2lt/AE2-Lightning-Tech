package com.moakiee.ae2lt.logic.craft;

import java.util.ArrayList;
import java.util.List;

import appeng.api.crafting.IPatternDetails;

public final class MatrixPatternStorageUnit implements MatrixPatternCore {
    public static final int T1_CAPACITY = 36;
    public static final int T2_CAPACITY = 72;

    private final MatrixPatternStorageTier tier;
    private final IPatternDetails[] slots;

    public MatrixPatternStorageUnit(int capacity) {
        this(MatrixPatternStorageTier.CUSTOM, capacity);
    }

    private MatrixPatternStorageUnit(MatrixPatternStorageTier tier, int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.tier = tier;
        this.slots = new IPatternDetails[capacity];
    }

    public static MatrixPatternStorageUnit t1() {
        return new MatrixPatternStorageUnit(MatrixPatternStorageTier.T1, T1_CAPACITY);
    }

    public static MatrixPatternStorageUnit t2() {
        return new MatrixPatternStorageUnit(MatrixPatternStorageTier.T2, T2_CAPACITY);
    }

    public MatrixPatternStorageTier tier() {
        return tier;
    }

    public int capacity() {
        return slots.length;
    }

    public int usedSlots() {
        int used = 0;
        for (var pattern : slots) {
            if (pattern != null) {
                used++;
            }
        }
        return used;
    }

    public int freeSlots() {
        return capacity() - usedSlots();
    }

    public boolean isEmpty() {
        return usedSlots() == 0;
    }

    public boolean isFull() {
        return freeSlots() == 0;
    }

    public boolean insert(IPatternDetails pattern) {
        if (pattern == null) return false;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                slots[i] = pattern;
                return true;
            }
        }
        return false;
    }

    public IPatternDetails get(int slot) {
        if (slot < 0 || slot >= slots.length) return null;
        return slots[slot];
    }

    public IPatternDetails extract(int slot) {
        if (slot < 0 || slot >= slots.length) return null;
        var pattern = slots[slot];
        slots[slot] = null;
        return pattern;
    }

    public boolean canUpgradeToT2() {
        return tier == MatrixPatternStorageTier.T1;
    }

    public MatrixPatternStorageUnit upgradeToT2() {
        if (!canUpgradeToT2()) return this;

        var upgraded = MatrixPatternStorageUnit.t2();
        for (int i = 0; i < slots.length; i++) {
            upgraded.slots[i] = slots[i];
        }
        return upgraded;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        var result = new ArrayList<IPatternDetails>();
        for (var pattern : slots) {
            if (pattern != null) {
                result.add(pattern);
            }
        }
        return List.copyOf(result);
    }
}
