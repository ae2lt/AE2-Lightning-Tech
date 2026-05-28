package com.moakiee.ae2lt.overload.armor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ArmorPhaseFlightRulesTest {

    @Test
    void activeStateSyncsOnlyWhenItChanges() {
        assertTrue(ArmorPhaseFlightRules.shouldSyncClientActiveState(true, true, false));
        assertTrue(ArmorPhaseFlightRules.shouldSyncClientActiveState(false, true, false));
        assertFalse(ArmorPhaseFlightRules.shouldSyncClientActiveState(true, false, false));
        assertFalse(ArmorPhaseFlightRules.shouldSyncClientActiveState(false, false, false));
    }

    @Test
    void activePredictiveMovementSyncsEvenWhenUnchanged() {
        assertTrue(ArmorPhaseFlightRules.shouldSyncClientActiveState(true, false, true));
        assertFalse(ArmorPhaseFlightRules.shouldSyncClientActiveState(false, false, true));
    }
}
