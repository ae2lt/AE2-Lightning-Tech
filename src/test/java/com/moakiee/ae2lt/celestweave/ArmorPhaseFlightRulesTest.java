package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ArmorPhaseFlightRulesTest {
    @Test
    void phaseModeRequiresAnActiveModuleAndActualFlight() {
        assertTrue(ArmorPhaseFlightRules.clientPhaseStateActive(true, true, true));
        assertFalse(ArmorPhaseFlightRules.clientPhaseStateActive(false, true, true));
        assertFalse(ArmorPhaseFlightRules.clientPhaseStateActive(true, false, true));
    }

    @Test
    void phaseModeCanBeDisabledWithoutDisablingTheFlightModule() {
        assertFalse(ArmorPhaseFlightRules.clientPhaseStateActive(true, true, false));
    }
}
