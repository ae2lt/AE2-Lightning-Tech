package com.moakiee.ae2lt.machine.lightningassembly;

final class LightningAssemblySlotLimits {
    static final int SLOT_CATALYST = 9;
    static final int LARGE_SLOT_LIMIT = 8192;
    static final int MATRIX_SLOT_LIMIT = 1;

    private LightningAssemblySlotLimits() {}

    static int getSlotLimit(int slot) {
        return slot == SLOT_CATALYST ? MATRIX_SLOT_LIMIT : LARGE_SLOT_LIMIT;
    }
}
