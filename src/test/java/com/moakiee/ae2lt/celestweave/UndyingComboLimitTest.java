package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UndyingComboLimitTest {
    @Test
    void defaultTwoHundredTickWindowSaturatesAtTwentyTriggers() {
        assertEquals(19, CelestweaveArmorUndyingHandler.capComboIndexForWindow(19, 200));
        assertEquals(20, CelestweaveArmorUndyingHandler.capComboIndexForWindow(20, 200));
        assertEquals(20, CelestweaveArmorUndyingHandler.capComboIndexForWindow(21, 200));
        assertEquals(20, CelestweaveArmorUndyingHandler.capComboIndexForWindow(Integer.MAX_VALUE, 200));
    }

    @Test
    void configuredWindowUsesTheTenTickProtectionCadence() {
        assertEquals(1, CelestweaveArmorUndyingHandler.capComboIndexForWindow(10, 1));
        assertEquals(1, CelestweaveArmorUndyingHandler.capComboIndexForWindow(10, 10));
        assertEquals(2, CelestweaveArmorUndyingHandler.capComboIndexForWindow(10, 11));
        assertEquals(40, CelestweaveArmorUndyingHandler.capComboIndexForWindow(100, 400));
    }
}
