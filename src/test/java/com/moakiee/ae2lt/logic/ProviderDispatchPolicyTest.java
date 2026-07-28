package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProviderDispatchPolicyTest {

    @Test
    void evenSharesGiveOneHundredTargetsOneCopyEach() {
        long remaining = 100;
        long[] accepted = new long[100];
        int visitBudget = ProviderDispatchPolicy.targetVisitBudget(accepted.length);
        for (int visit = 0; visit < visitBudget && remaining > 0; visit++) {
            long share = ProviderDispatchPolicy.evenShare(
                    remaining, visitBudget - visit);
            accepted[visit] = share;
            remaining -= share;
        }

        assertEquals(0, remaining);
        for (long amount : accepted) {
            assertEquals(1L, amount);
        }
    }

    @Test
    void oneReadyTargetReceivesTheWholeBatch() {
        assertEquals(100, ProviderDispatchPolicy.evenShare(100, 1));
    }

    @Test
    void rejectedTargetsShareIsRedistributedAcrossTheRemainingPass() {
        long remaining = 100L;
        long[] accepted = new long[4];
        int visitBudget = ProviderDispatchPolicy.targetVisitBudget(accepted.length);

        for (int visit = 0; visit < visitBudget; visit++) {
            long share = ProviderDispatchPolicy.evenShare(
                    remaining, visitBudget - visit);
            accepted[visit] = visit == 0 ? 0L : share;
            remaining -= accepted[visit];
        }

        assertEquals(0L, remaining);
        assertEquals(0L, accepted[0]);
        assertEquals(34L, accepted[1]);
        assertEquals(33L, accepted[2]);
        assertEquals(33L, accepted[3]);
    }

    @Test
    void rotatingStartBalancesTwoTargetsAcrossCalls() {
        long[] accepted = new long[2];
        int cursor = 0;

        for (int call = 0; call < 2; call++) {
            long remaining = 100;
            int visitBudget = ProviderDispatchPolicy.targetVisitBudget(accepted.length);
            for (int visit = 0; visit < visitBudget && remaining > 0; visit++) {
                long share = ProviderDispatchPolicy.evenShare(
                        remaining, visitBudget - visit);
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
            long actual = ProviderDispatchPolicy.nextRampChunk(fullCredit, remaining);
            assertEquals(step, actual);
            remaining -= actual;
            fullCredit += actual;
        }
        assertEquals(0, remaining);
    }

    @Test
    void rejectedLargerChunkEndsTheCallWithoutSearchingTheTail() {
        long availableCopies = 13;
        long acceptedCopies = 0;
        long fullCredit = 0;
        long rejectedChunk = 0;

        while (acceptedCopies < 100) {
            long chunk = ProviderDispatchPolicy.nextRampChunk(
                    fullCredit, 100 - acceptedCopies);
            if (chunk > availableCopies - acceptedCopies) {
                rejectedChunk = chunk;
                break;
            }
            acceptedCopies += chunk;
            assertTrue(ProviderDispatchPolicy.mayContinueRamp(
                    chunk, chunk, true));
            fullCredit = ProviderDispatchPolicy.addRampCredit(
                    fullCredit, chunk);
        }

        assertEquals(8, acceptedCopies);
        assertEquals(8, rejectedChunk);

        // A later provider call does not retain the rejected size or previous
        // capacity guess: it restarts at one copy.
        assertEquals(
                1,
                ProviderDispatchPolicy.nextRampChunk(
                        0, availableCopies - acceptedCopies));
    }

    @Test
    void rejectionOrDefensiveOverflowCannotGrowTheCurrentRamp() {
        assertFalse(ProviderDispatchPolicy.mayContinueRamp(8, 0, false));
        assertFalse(ProviderDispatchPolicy.mayContinueRamp(8, 4, false));
        assertFalse(ProviderDispatchPolicy.mayContinueRamp(8, 8, false));
        assertFalse(ProviderDispatchPolicy.mayContinueRamp(8, 7, true));
        assertTrue(ProviderDispatchPolicy.mayContinueRamp(8, 8, true));
    }

    @Test
    void rampCreditSaturatesInsteadOfOverflowing() {
        assertEquals(
                Long.MAX_VALUE,
                ProviderDispatchPolicy.addRampCredit(Long.MAX_VALUE - 2, 8));
    }

    @Test
    void visitBudgetCoversTheInitialReadySnapshotExactlyOnce() {
        assertEquals(0, ProviderDispatchPolicy.targetVisitBudget(0));
        assertEquals(1, ProviderDispatchPolicy.targetVisitBudget(1));
        assertEquals(100, ProviderDispatchPolicy.targetVisitBudget(100));
        assertEquals(1_024, ProviderDispatchPolicy.targetVisitBudget(1_024));
    }

    @Test
    void oneBatchKeepsOneHundredMachinesFullAt128And256RecipesPerTick() {
        assertSustainedMachineThroughput(128);
        assertSustainedMachineThroughput(256);
    }

    @Test
    void oneHundredTotalCopiesMeansOneCopyPerMachineNotOneHundredPerMachine() {
        int machines = 100;
        long remaining = 100;
        long accepted = 0;
        int visitBudget = ProviderDispatchPolicy.targetVisitBudget(machines);

        for (int visit = 0; visit < visitBudget && remaining > 0L; visit++) {
            long share = ProviderDispatchPolicy.evenShare(
                    remaining, visitBudget - visit);
            accepted += share;
            remaining -= share;
        }

        assertEquals(100L, accepted);
        assertEquals(0L, remaining);
        assertTrue(accepted < (long) machines * 128L);
    }

    @Test
    void aggregateSimulationRequiresTheCompleteRequestedAmount() {
        assertTrue(ProviderDispatchPolicy.acceptsCompleteAmount(8, 8));
        assertTrue(ProviderDispatchPolicy.acceptsCompleteAmount(8, 9));
        assertFalse(ProviderDispatchPolicy.acceptsCompleteAmount(8, 7));
        assertFalse(ProviderDispatchPolicy.acceptsCompleteAmount(8, 1));
        assertFalse(ProviderDispatchPolicy.acceptsCompleteAmount(8, 0));
        assertFalse(ProviderDispatchPolicy.acceptsCompleteAmount(0, 0));
    }

    @Test
    void blockedOverflowBacksOffAdditivelyWithinFiveToTwentyTicks() {
        int delay = ProviderDispatchPolicy.initialOverflowRetryDelay();
        assertEquals(5, delay);

        delay = ProviderDispatchPolicy.nextOverflowRetryDelay(
                delay, ProviderDispatchPolicy.OverflowAttemptResult.BLOCKED);
        assertEquals(10, delay);
        delay = ProviderDispatchPolicy.nextOverflowRetryDelay(
                delay, ProviderDispatchPolicy.OverflowAttemptResult.BLOCKED);
        assertEquals(15, delay);
        delay = ProviderDispatchPolicy.nextOverflowRetryDelay(
                delay, ProviderDispatchPolicy.OverflowAttemptResult.BLOCKED);
        assertEquals(20, delay);
        delay = ProviderDispatchPolicy.nextOverflowRetryDelay(
                delay, ProviderDispatchPolicy.OverflowAttemptResult.BLOCKED);
        assertEquals(20, delay);
    }

    @Test
    void anyOverflowProgressResetsRetryDelayToFiveTicks() {
        assertEquals(
                5,
                ProviderDispatchPolicy.nextOverflowRetryDelay(
                        20, ProviderDispatchPolicy.OverflowAttemptResult.PROGRESSED));
        assertEquals(
                10,
                ProviderDispatchPolicy.nextOverflowRetryDelay(
                        5, ProviderDispatchPolicy.OverflowAttemptResult.BLOCKED));
    }

    @Test
    void invalidOrHugeRetryStateStillStaysWithinPolicyBounds() {
        assertEquals(
                10,
                ProviderDispatchPolicy.nextOverflowRetryDelay(
                        Integer.MIN_VALUE,
                        ProviderDispatchPolicy.OverflowAttemptResult.BLOCKED));
        assertEquals(
                20,
                ProviderDispatchPolicy.nextOverflowRetryDelay(
                        Integer.MAX_VALUE,
                        ProviderDispatchPolicy.OverflowAttemptResult.BLOCKED));
    }

    @Test
    void overflowDecisionDirtiesOnlyWhenOwnedContentsChange() {
        var blocked = ProviderDispatchPolicy.OverflowAttemptResult.BLOCKED;
        assertFalse(blocked.removeBucket());
        assertTrue(blocked.reschedule());
        assertEquals(10, ProviderDispatchPolicy.nextOverflowRetryDelay(5, blocked));
        assertFalse(blocked.persistentStateChanged());

        var progressed = ProviderDispatchPolicy.OverflowAttemptResult.PROGRESSED;
        assertFalse(progressed.removeBucket());
        assertTrue(progressed.reschedule());
        assertEquals(5, ProviderDispatchPolicy.nextOverflowRetryDelay(20, progressed));
        assertTrue(progressed.persistentStateChanged());

        var cleared = ProviderDispatchPolicy.OverflowAttemptResult.CLEARED;
        assertTrue(cleared.removeBucket());
        assertFalse(cleared.reschedule());
        assertEquals(0, ProviderDispatchPolicy.nextOverflowRetryDelay(20, cleared));
        assertTrue(cleared.persistentStateChanged());
    }

    private static void assertSustainedMachineThroughput(int recipesPerTick) {
        int machines = 100;
        long capacityPerInput = 1_000L;
        long[] storedA = new long[machines];
        long[] storedB = new long[machines];

        long initialLeftover = dispatchAcrossMachines(
                storedA, storedB, capacityPerInput,
                capacityPerInput * machines);
        assertEquals(0L, initialLeftover);

        for (int tick = 0; tick < 40; tick++) {
            long completed = 0L;
            for (int machine = 0; machine < machines; machine++) {
                long recipes = Math.min(
                        recipesPerTick,
                        Math.min(storedA[machine], storedB[machine]));
                storedA[machine] -= recipes;
                storedB[machine] -= recipes;
                completed += recipes;
            }
            assertEquals((long) machines * recipesPerTick, completed);

            long leftover = dispatchAcrossMachines(
                    storedA, storedB, capacityPerInput,
                    (long) machines * recipesPerTick);
            assertEquals(0L, leftover);
            for (int machine = 0; machine < machines; machine++) {
                assertEquals(capacityPerInput, storedA[machine]);
                assertEquals(capacityPerInput, storedB[machine]);
            }
        }
    }

    private static long dispatchAcrossMachines(
            long[] storedA,
            long[] storedB,
            long capacityPerInput,
            long requestedCopies) {
        long remaining = requestedCopies;
        int visitBudget = ProviderDispatchPolicy.targetVisitBudget(storedA.length);

        for (int visit = 0; visit < visitBudget && remaining > 0L; visit++) {
            long share = ProviderDispatchPolicy.evenShare(
                    remaining, visitBudget - visit);
            long freeCopies = Math.min(
                    capacityPerInput - storedA[visit],
                    capacityPerInput - storedB[visit]);
            long accepted = dispatchOneMachine(share, freeCopies);
            storedA[visit] += accepted;
            storedB[visit] += accepted;
            remaining -= accepted;
        }
        return remaining;
    }

    private static long dispatchOneMachine(long requestedCopies, long freeCopies) {
        long accepted = 0L;
        long fullCredit = 0L;
        while (accepted < requestedCopies) {
            long chunk = ProviderDispatchPolicy.nextRampChunk(
                    fullCredit, requestedCopies - accepted);
            if (!ProviderDispatchPolicy.acceptsCompleteAmount(
                    chunk, Math.min(chunk, freeCopies - accepted))) {
                break;
            }
            accepted += chunk;
            if (!ProviderDispatchPolicy.mayContinueRamp(chunk, chunk, true)) {
                break;
            }
            fullCredit = ProviderDispatchPolicy.addRampCredit(
                    fullCredit, chunk);
        }
        return accepted;
    }
}
