package com.moakiee.ae2lt.celestweave.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FlightAbilityRestoreRulesTest {

    @Test
    void nonGameModeRestoreIgnoresFlightCapturedFromCreative() {
        var target = FlightAbilityRestoreRules.targetForNonGameModePlayer(
                true,
                true,
                true,
                false);

        assertFalse(target.mayfly());
        assertFalse(target.flying());
    }

    @Test
    void nonGameModeRestoreKeepsPreviousNonCreativeFlight() {
        var target = FlightAbilityRestoreRules.targetForNonGameModePlayer(
                true,
                true,
                false,
                false);

        assertTrue(target.mayfly());
        assertTrue(target.flying());
    }

    @Test
    void siblingFlightModuleStillKeepsFlightAfterCreativeBaselineRestore() {
        var target = FlightAbilityRestoreRules.targetForNonGameModePlayer(
                true,
                true,
                true,
                true);

        assertTrue(target.mayfly());
        assertTrue(target.flying());
    }
}
