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
    void crouchChordRequiresActiveModuleAndBothKeys() {
        assertTrue(PhaseFlightControlRules.isCrouchChord(true, true, true));
        assertFalse(PhaseFlightControlRules.isCrouchChord(false, true, true));
        assertFalse(PhaseFlightControlRules.isCrouchChord(true, false, true));
        assertFalse(PhaseFlightControlRules.isCrouchChord(true, true, false));
    }

    @Test
    void flightLockOnlyPreservesNonPhaseHoverOnLanding() {
        assertTrue(PhaseFlightControlRules.preserveFlightOnLanding(true, false, true));
        assertFalse(PhaseFlightControlRules.preserveFlightOnLanding(false, false, true));
        assertFalse(PhaseFlightControlRules.preserveFlightOnLanding(true, true, true));
        assertFalse(PhaseFlightControlRules.preserveFlightOnLanding(true, false, false));
    }

    @Test
    void effectiveFlightUsesPrivateIntentOnlyWhileLocked() {
        assertTrue(PhaseFlightControlRules.effectiveFlying(true, true, true, false));
        assertFalse(PhaseFlightControlRules.effectiveFlying(true, true, false, true));
        assertTrue(PhaseFlightControlRules.effectiveFlying(true, false, false, true));
        assertFalse(PhaseFlightControlRules.effectiveFlying(true, false, true, false));
        assertTrue(PhaseFlightControlRules.effectiveFlying(false, true, false, true));
    }

    @Test
    void publicMayflyIsExternalOnlyWhenCelestweaveDoesNotOwnIt() {
        assertTrue(PhaseFlightControlRules.hasExternalFlightSource(false, true));
        assertFalse(PhaseFlightControlRules.hasExternalFlightSource(true, true));
        assertFalse(PhaseFlightControlRules.hasExternalFlightSource(false, false));
        assertFalse(PhaseFlightControlRules.hasExternalFlightSource(true, false));
    }

    @Test
    void lockedNonPhaseHoverCanStillCrouchOnTheGround() {
        assertTrue(PhaseFlightControlRules.exposeGroundCrouch(true, false, true, true, true));
        assertFalse(PhaseFlightControlRules.exposeGroundCrouch(false, false, true, true, true));
        assertFalse(PhaseFlightControlRules.exposeGroundCrouch(true, true, true, true, true));
        assertFalse(PhaseFlightControlRules.exposeGroundCrouch(true, false, false, true, true));
        assertFalse(PhaseFlightControlRules.exposeGroundCrouch(true, false, true, false, true));
        assertFalse(PhaseFlightControlRules.exposeGroundCrouch(true, false, true, true, false));
    }
}
