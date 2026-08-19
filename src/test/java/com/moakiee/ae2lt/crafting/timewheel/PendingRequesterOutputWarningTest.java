package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PendingRequesterOutputWarningTest {
    @Test
    void hidesTheExpectedShortRecursiveInsertionDelay() {
        var warning = new PendingRequesterOutputWarning();

        assertFalse(warning.update(100L, true));
        assertFalse(warning.update(100L, true));
        assertFalse(warning.update(119L, true));
        assertFalse(warning.update(120L, false));
    }

    @Test
    void reportsOutputThatRemainsBlocked() {
        var warning = new PendingRequesterOutputWarning();

        assertFalse(warning.update(100L, true));
        assertTrue(warning.update(120L, true));
    }

    @Test
    void startsANewGracePeriodAfterRecovery() {
        var warning = new PendingRequesterOutputWarning();

        assertFalse(warning.update(100L, true));
        assertTrue(warning.update(120L, true));
        assertFalse(warning.update(121L, false));
        assertFalse(warning.update(122L, true));
    }
}
