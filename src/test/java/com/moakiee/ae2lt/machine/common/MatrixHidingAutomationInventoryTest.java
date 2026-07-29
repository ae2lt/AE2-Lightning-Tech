package com.moakiee.ae2lt.machine.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MatrixHidingAutomationInventoryTest {

    @Test
    void onlyMatrixSlotIsHiddenFromAutomationInventoryScans() {
        int matrixSlot = 9;

        assertFalse(MatrixHidingAutomationInventory.isSlotVisibleToAutomation(
                matrixSlot, matrixSlot));
        assertTrue(MatrixHidingAutomationInventory.isSlotVisibleToAutomation(
                0, matrixSlot));
        assertTrue(MatrixHidingAutomationInventory.isSlotVisibleToAutomation(
                10, matrixSlot));
    }
}
