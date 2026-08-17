package com.moakiee.ae2lt.celestweave.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FlightAbilityRestoreRulesTest {
    @Test
    void removedExternalSourceDoesNotLeakFlightPermission() {
        var target = FlightAbilityRestoreRules.targetAfterReleaseProbe(
                false, false, true);

        assertFalse(target.mayfly());
        assertFalse(target.flying());
    }

    @Test
    void reassertedExternalSourceKeepsCurrentFlight() {
        var target = FlightAbilityRestoreRules.targetAfterReleaseProbe(
                false, true, true);

        assertTrue(target.mayfly());
        assertTrue(target.flying());
    }

    @Test
    void newlyActivatedExternalSourceIsDetectedDuringProbe() {
        var target = FlightAbilityRestoreRules.targetAfterReleaseProbe(
                false, true, false);

        assertTrue(target.mayfly());
        assertFalse(target.flying());
    }

    @Test
    void currentGameModeFlightDoesNotDependOnExternalReassertion() {
        var target = FlightAbilityRestoreRules.targetAfterReleaseProbe(
                true, false, true);

        assertTrue(target.mayfly());
        assertTrue(target.flying());
    }
}
