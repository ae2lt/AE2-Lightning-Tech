package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PhaseShieldChargeWindowTest {
    @Test
    void billsOnlyTheNewHighWaterMarkInsideOneSecond() {
        var first = PhaseShieldChargeWindow.quote(PhaseShieldChargeWindow.State.EMPTY, 100L, 100.0D);
        assertEquals(2_000_000L, first.feCost());
        assertEquals(100L, first.ehvCost());

        var smaller = PhaseShieldChargeWindow.quote(first.nextState(), 110L, 80.0D);
        assertEquals(0L, smaller.feCost());
        assertEquals(0L, smaller.ehvCost());

        var larger = PhaseShieldChargeWindow.quote(smaller.nextState(), 115L, 150.0D);
        assertEquals(1_000_000L, larger.feCost());
        assertEquals(50L, larger.ehvCost());
    }

    @Test
    void capsTheWholeWindowAtTheFirstUndyingTriggerCost() {
        var capped = PhaseShieldChargeWindow.quote(
                PhaseShieldChargeWindow.State.EMPTY,
                100L,
                200_000.0D);
        assertEquals(ArmorOverloadRules.UNDYING_TRIGGER_COST_FE, capped.feCost());
        assertEquals(ArmorOverloadRules.UNDYING_TRIGGER_COST_EHV, capped.ehvCost());

        var evenLarger = PhaseShieldChargeWindow.quote(capped.nextState(), 110L, 300_000.0D);
        assertEquals(0L, evenLarger.feCost());
        assertEquals(0L, evenLarger.ehvCost());
    }

    @Test
    void startsAFullNewChargeAtTheTwentyTickBoundary() {
        var first = PhaseShieldChargeWindow.quote(PhaseShieldChargeWindow.State.EMPTY, 100L, 100.0D);
        var nextWindow = PhaseShieldChargeWindow.quote(first.nextState(), 120L, 100.0D);

        assertEquals(2_000_000L, nextWindow.feCost());
        assertEquals(100L, nextWindow.ehvCost());
    }

    @Test
    void extremeDamageSaturatesAtTheConfiguredCaps() {
        var quote = PhaseShieldChargeWindow.quote(
                PhaseShieldChargeWindow.State.EMPTY,
                Long.MAX_VALUE - 10L,
                Double.POSITIVE_INFINITY);

        assertEquals(ArmorOverloadRules.UNDYING_TRIGGER_COST_FE, quote.feCost());
        assertEquals(ArmorOverloadRules.UNDYING_TRIGGER_COST_EHV, quote.ehvCost());
        assertEquals(Long.MAX_VALUE, quote.nextState().windowUntil());
    }
}
