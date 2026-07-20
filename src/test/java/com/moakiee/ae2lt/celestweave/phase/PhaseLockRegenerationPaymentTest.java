package com.moakiee.ae2lt.celestweave.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;

class PhaseLockRegenerationPaymentTest {
    @Test
    void regenerationCostsOneMillionFeAndSixteenEhv() {
        assertEquals(1_000_000L, ArmorOverloadRules.PHASE_LOCK_REGEN_COST_FE);
        assertEquals(16L, ArmorOverloadRules.PHASE_LOCK_REGEN_COST_EHV);
    }

    @Test
    void doesNotConsumeEhvWhenFePaymentFails() {
        var ehvConsumed = new AtomicBoolean();
        var feRefunded = new AtomicBoolean();

        assertFalse(PhaseLockService.completeRegenerationPayment(
                false,
                () -> {
                    ehvConsumed.set(true);
                    return true;
                },
                () -> feRefunded.set(true)));
        assertFalse(ehvConsumed.get());
        assertFalse(feRefunded.get());
    }

    @Test
    void refundsFeWhenEhvPaymentFails() {
        var feRefunded = new AtomicBoolean();

        assertFalse(PhaseLockService.completeRegenerationPayment(
                true,
                () -> false,
                () -> feRefunded.set(true)));
        assertTrue(feRefunded.get());
    }

    @Test
    void keepsBothPaymentsWhenTheySucceed() {
        var feRefunded = new AtomicBoolean();

        assertTrue(PhaseLockService.completeRegenerationPayment(
                true,
                () -> true,
                () -> feRefunded.set(true)));
        assertFalse(feRefunded.get());
    }
}
