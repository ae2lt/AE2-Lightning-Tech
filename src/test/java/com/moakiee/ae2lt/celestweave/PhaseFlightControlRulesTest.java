package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PhaseFlightControlRulesTest {
    @Test
    void flightCanOnlyBeToggledOffOutsideBlocksWhilePhaseModeIsEnabled() {
        assertTrue(PhaseFlightControlRules.rejectFlightToggle(true, true, false));
        assertFalse(PhaseFlightControlRules.rejectFlightToggle(true, false, false));
        assertFalse(PhaseFlightControlRules.rejectFlightToggle(false, true, false));
        assertFalse(PhaseFlightControlRules.rejectFlightToggle(true, true, true));
    }

    @Test
    void vanillaLandingExitIsSuppressedOnlyByPhaseMode() {
        assertTrue(PhaseFlightControlRules.suppressLandingExit(true));
        assertFalse(PhaseFlightControlRules.suppressLandingExit(false));
    }
}
