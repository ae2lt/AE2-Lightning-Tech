package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FinalOutputProgressTest {
    @Test
    void standaloneJobCompletesFromTheAmountOfferedToMeStorage() {
        assertEquals(1L, FinalOutputProgress.completedAmount(true, 1L, 0L));
        assertEquals(1_000L, FinalOutputProgress.completedAmount(true, 1_000L, 0L));
    }

    @Test
    void requesterJobCompletesOnlyTheAmountAcceptedByItsRequester() {
        assertEquals(3L, FinalOutputProgress.completedAmount(false, 10L, 3L));
        assertEquals(10L, FinalOutputProgress.completedAmount(false, 10L, 20L));
    }

    @Test
    void requesterRemainderIsDeferredWithoutAffectingStandaloneJobs() {
        assertEquals(7L, FinalOutputProgress.deferredRequesterAmount(false, 10L, 3L));
        assertEquals(0L, FinalOutputProgress.deferredRequesterAmount(true, 10L, 0L));
    }

    @Test
    void terminalRecoveryNeverConsumesProtectedSeedOrRetainedOutput() {
        assertEquals(64L,
                FinalOutputProgress.recoverableInventoryAmount(192L, 64L, 64L, 128L));
        assertEquals(0L,
                FinalOutputProgress.recoverableInventoryAmount(128L, 64L, 64L, 128L));
        assertEquals(20L,
                FinalOutputProgress.recoverableInventoryAmount(1_000L, 0L, 0L, 20L));
    }

    @Test
    void terminalRecoveryHandlesSaturatedProtectedAccounting() {
        assertEquals(0L,
                FinalOutputProgress.recoverableInventoryAmount(
                        Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
    }
}
