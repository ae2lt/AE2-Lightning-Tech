package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExtendedAEPlusEncodingCompatTest {
    @Test
    void armsAndCleansEaepOneShotSuppression() {
        var menu = new SimulatedEaepMenu();

        try (var ignored = ExtendedAEPlusEncodingCompat.suppressAutomaticUpload(menu)) {
            assertTrue(menu.pending);
        }

        assertFalse(menu.pending);
    }

    @Test
    void cleanupIsHarmlessWhenEaepAlreadyConsumedTheFlag() {
        var menu = new SimulatedEaepMenu();

        try (var ignored = ExtendedAEPlusEncodingCompat.suppressAutomaticUpload(menu)) {
            assertTrue(menu.eap$consumeShiftUploadFlag());
        }

        assertFalse(menu.pending);
    }

    @Test
    void absentEaepIsANoOp() {
        try (var ignored = ExtendedAEPlusEncodingCompat.suppressAutomaticUpload(new Object())) {
            assertTrue(true);
        }
    }

    public static final class SimulatedEaepMenu {
        private boolean pending;

        public void eap$clientSetShiftUpload(boolean pending) {
            this.pending = pending;
        }

        public boolean eap$consumeShiftUploadFlag() {
            boolean consumed = pending;
            pending = false;
            return consumed;
        }
    }
}
