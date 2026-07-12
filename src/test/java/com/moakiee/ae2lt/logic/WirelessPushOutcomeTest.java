package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WirelessPushOutcomeTest {

    @Test
    void globalAbortStopsScanningWithoutPenalizingTheTarget() {
        assertFalse(WirelessPushOutcome.GLOBAL_ABORT.consumesTargetAttempt());
    }

    @Test
    void softFailurePenalizesOnlyTheAttemptedTargetPattern() {
        assertTrue(WirelessPushOutcome.SOFT_FAIL.consumesTargetAttempt());
    }
}
