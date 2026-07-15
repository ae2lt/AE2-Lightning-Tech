package com.moakiee.ae2lt.machine.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LightningCollapseMatrixHostTest {

    @Test
    void desiredCountIsClampedToEachMachinesOwnSlotLimit() {
        assertEquals(1, LightningCollapseMatrixCounts.clampToSlotLimit(32, 1));
        assertEquals(32, LightningCollapseMatrixCounts.clampToSlotLimit(64, 32));
    }

    @Test
    void desiredCountCannotBecomeNegative() {
        assertEquals(0, LightningCollapseMatrixCounts.clampToSlotLimit(-1, 32));
        assertEquals(0, LightningCollapseMatrixCounts.clampToSlotLimit(1, -1));
    }
}
