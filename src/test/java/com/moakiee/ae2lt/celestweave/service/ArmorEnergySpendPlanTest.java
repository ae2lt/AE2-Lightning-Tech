package com.moakiee.ae2lt.celestweave.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ArmorEnergySpendPlanTest {
    @Test
    void combinesArmorBuffersAndBoundNetworkCapacityForAnActiveCost() {
        var plan = ArmorEnergySpendPlan.create(
                2_000_000_000L,
                List.of(
                        new ArmorEnergySpendPlan.Source(0, 1_000_000_000L),
                        new ArmorEnergySpendPlan.Source(1, 30_000_000L),
                        new ArmorEnergySpendPlan.Source(2, 970_000_000L)));

        assertTrue(plan.canPay());
        assertEquals(
                List.of(
                        new ArmorEnergySpendPlan.Debit(0, 1_000_000_000L),
                        new ArmorEnergySpendPlan.Debit(1, 30_000_000L),
                        new ArmorEnergySpendPlan.Debit(2, 970_000_000L)),
                plan.debits());
    }

    @Test
    void refusesTheActiveCostWithoutMutatingAnySourceWhenTheCombinedTotalIsShort() {
        var plan = ArmorEnergySpendPlan.create(
                2_000_000_000L,
                List.of(
                        new ArmorEnergySpendPlan.Source(0, 1_000_000_000L),
                        new ArmorEnergySpendPlan.Source(1, 999_999_999L)));

        assertFalse(plan.canPay());
        assertTrue(plan.debits().isEmpty());
    }
}
