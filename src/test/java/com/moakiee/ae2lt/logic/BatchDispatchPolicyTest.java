package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BatchDispatchPolicyTest {

    @Test
    void geometricSharesFinishOneHundredCopiesWithoutScanningOneHundredTargets() {
        long remaining = 100;
        int attempts = 0;
        while (remaining > 0) {
            long share = BatchDispatchPolicy.geometricShare(remaining, 100);
            remaining -= share;
            attempts++;
        }

        assertEquals(0, remaining);
        assertEquals(7, attempts);
        assertTrue(attempts <= BatchDispatchPolicy.targetAttemptBudget(100));
    }

    @Test
    void oneReadyTargetReceivesTheWholeBatch() {
        assertEquals(100, BatchDispatchPolicy.geometricShare(100, 1));
    }

    @Test
    void rotatingStartBalancesTwoTargetsAcrossCalls() {
        long[] accepted = new long[2];
        int cursor = 0;

        for (int call = 0; call < 2; call++) {
            long remaining = 100;
            int attempts = BatchDispatchPolicy.targetAttemptBudget(remaining);
            while (remaining > 0 && attempts-- > 0) {
                long share = BatchDispatchPolicy.geometricShare(remaining, 2);
                accepted[cursor] += share;
                remaining -= share;
                cursor = (cursor + 1) % accepted.length;
            }
        }

        assertEquals(100, accepted[0]);
        assertEquals(100, accepted[1]);
    }

    @Test
    void rampNeverUsesHistoricalCapacity() {
        long remaining = 100;
        long fullCredit = 0;
        long[] expected = {1, 1, 2, 4, 8, 16, 32, 36};

        for (long step : expected) {
            long actual = BatchDispatchPolicy.nextRampChunk(fullCredit, remaining);
            assertEquals(step, actual);
            remaining -= actual;
            fullCredit += actual;
        }
        assertEquals(0, remaining);
    }

    @Test
    void targetAttemptBudgetDependsOnCopiesNotDeviceCount() {
        assertEquals(1, BatchDispatchPolicy.targetAttemptBudget(1));
        assertEquals(8, BatchDispatchPolicy.targetAttemptBudget(100));
        assertEquals(15, BatchDispatchPolicy.targetAttemptBudget(16_384));
        assertEquals(63, BatchDispatchPolicy.targetAttemptBudget(Long.MAX_VALUE));
    }
}
