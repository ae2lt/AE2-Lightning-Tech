package com.moakiee.ae2lt.celestweave.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FlightAbilityRestoreRulesTest {
    @Test
    void survivalDoesNotKeepFlightCapturedFromCreativeMode() {
        var target = FlightAbilityRestoreRules.targetForForgePlayer(
                true, true, false, true, false);

        assertFalse(target.mayfly());
        assertFalse(target.flying());
    }

    @Test
    void externalForgeFlightAndCurrentIntentSurviveHandoff() {
        var target = FlightAbilityRestoreRules.targetForForgePlayer(
                true, false, false, true, false);

        assertTrue(target.mayfly());
        assertTrue(target.flying());
    }

    @Test
    void siblingCelestweaveModuleKeepsCurrentFlight() {
        var target = FlightAbilityRestoreRules.targetForForgePlayer(
                false, false, false, true, true);

        assertTrue(target.mayfly());
        assertTrue(target.flying());
    }
}
