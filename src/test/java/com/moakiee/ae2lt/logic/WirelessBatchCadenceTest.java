package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

class WirelessBatchCadenceTest {
    private static final String TARGET = "target";
    private final IPatternDetails pattern = new EmptyPattern();

    @Test
    void schedulerDelayBetweenCleanSuccessesDoesNotInflateCoverage() {
        var cadence = new WirelessBatchCadence<String>();

        assertEquals(1, success(cadence, 0L, 1));
        assertEquals(1, success(cadence, 1L, 1));
        assertEquals(1, success(cadence, 2L, 2));
        assertEquals(1, success(cadence, 3L, 4));
        assertEquals(1, success(cadence, 4L, 8));
        assertEquals(1, success(cadence, 6L, 8));
    }

    @Test
    void rejectedGrowthProbeDoesNotInflateSafeRefillInterval() {
        var cadence = new WirelessBatchCadence<String>();
        assertEquals(1, success(cadence, 0L, 8));

        cadence.recordFailure(TARGET, pattern, 1L, 16);

        assertEquals(1, success(cadence, 5L, 8));
        assertEquals(1, success(cadence, 8L, 8));
    }

    @Test
    void rejectedProvenChunkCountsTowardObservedRefillInterval() {
        var cadence = new WirelessBatchCadence<String>();
        assertEquals(1, success(cadence, 0L, 8));

        assertEquals(1, cadence.recordFailure(TARGET, pattern, 1L, 8));
        assertEquals(1, cadence.recordFailure(TARGET, pattern, 2L, 8));
        assertEquals(1, cadence.recordFailure(TARGET, pattern, 3L, 8));

        assertEquals(3, success(cadence, 4L, 8));
        assertTrue(cadence.isExploratoryAttempt(TARGET, pattern));

        assertEquals(1, cadence.recordFailure(
                TARGET, pattern, 7L, 8, true));
        assertFalse(cadence.isExploratoryAttempt(TARGET, pattern));
        assertEquals(1, cadence.recordFailure(
                TARGET, pattern, 8L, 8, false));

        assertEquals(4, success(cadence, 9L, 8));
        assertTrue(cadence.isExploratoryAttempt(TARGET, pattern));
    }

    @Test
    void smallerRecoveryChunkScalesLearnedCoverageByCopies() {
        var cadence = new WirelessBatchCadence<String>();
        assertEquals(1, success(cadence, 0L, 8));
        assertEquals(1, success(cadence, 4L, 8));
        assertEquals(1, cadence.recordFailure(TARGET, pattern, 5L, 8));

        assertEquals(2, success(cadence, 6L, 4));
        assertEquals(1, success(cadence, 8L, 8));
    }

    @Test
    void recoveryAfterEarlyProbeMayScheduleTheNextEarlyProbe() {
        var cadence = new WirelessBatchCadence<String>();
        assertEquals(1, success(cadence, 0L, 8));
        assertEquals(1, cadence.recordFailure(TARGET, pattern, 1L, 8));
        assertEquals(4, success(cadence, 5L, 8));
        assertTrue(cadence.isExploratoryAttempt(TARGET, pattern));

        assertEquals(1, cadence.recordFailure(
                TARGET, pattern, 9L, 8, true));
        assertEquals(4, success(cadence, 10L, 8));
        assertTrue(cadence.isExploratoryAttempt(TARGET, pattern));
    }

    @Test
    void successfulEarlyProbesMayContinueReducingCoverage() {
        var cadence = new WirelessBatchCadence<String>();
        assertEquals(1, success(cadence, 0L, 8));
        assertEquals(1, cadence.recordFailure(TARGET, pattern, 1L, 8));
        assertEquals(4, success(cadence, 5L, 8));

        assertEquals(4, cadence.recordSuccess(
                TARGET, pattern, 9L, 8, true, false, true));
        assertTrue(cadence.isExploratoryAttempt(TARGET, pattern));
        assertEquals(3, cadence.recordSuccess(
                TARGET, pattern, 13L, 8, true, false, true));
        assertTrue(cadence.isExploratoryAttempt(TARGET, pattern));
    }

    @Test
    void idleHistoryResetsToOneCopyPerTickBaseline() {
        var cadence = new WirelessBatchCadence<String>();
        success(cadence, 0L, 1);
        success(cadence, 1L, 1);
        success(cadence, 2L, 2);
        success(cadence, 3L, 4);

        int coverage = success(cadence, 104L, 100);

        assertEquals(1, coverage);
    }

    @Test
    void oneHundredTicksWithoutAcceptanceEntersFillFallback() {
        var cadence = new WirelessBatchCadence<String>();
        assertEquals(1, success(cadence, 0L, 128));

        for (long tick = 1L; tick < 100L; tick++) {
            assertEquals(1, cadence.recordFailure(
                    TARGET, pattern, tick, 128));
            assertFalse(cadence.isFillFallback(TARGET, pattern));
        }

        assertTrue(cadence.shouldPreserveBatchHistory(
                TARGET, pattern, 100L));
        assertEquals(25, cadence.recordFailure(
                TARGET, pattern, 100L, 128));
        assertTrue(cadence.isFillFallback(TARGET, pattern));
        assertEquals(25, cadence.recordFailure(
                TARGET, pattern, 125L, 128));
    }

    @Test
    void smallProvenBatchStaysOnNormalLearnedCadence() {
        var cadence = new WirelessBatchCadence<String>();
        success(cadence, 0L, 32);

        for (long tick = 1L; tick <= 100L; tick++) {
            assertEquals(1, cadence.recordFailure(
                    TARGET, pattern, tick, 32));
        }

        assertFalse(cadence.isFillFallback(TARGET, pattern));
        assertFalse(cadence.shouldPreserveBatchHistory(
                TARGET, pattern, 100L));
    }

    @Test
    void twoConsecutiveFallbackSuccessesRestoreNormalCadence() {
        var cadence = new WirelessBatchCadence<String>();
        success(cadence, 0L, 128);
        for (long tick = 1L; tick <= 100L; tick++) {
            cadence.recordFailure(TARGET, pattern, tick, 128);
        }

        assertEquals(25, success(cadence, 125L, 128));
        assertTrue(cadence.isFillFallback(TARGET, pattern));
        assertEquals(1, success(cadence, 150L, 128));
        assertFalse(cadence.isFillFallback(TARGET, pattern));
        assertFalse(cadence.isExploratoryAttempt(TARGET, pattern));
    }

    private int success(
            WirelessBatchCadence<String> cadence,
            long tick,
            int copies) {
        return cadence.recordSuccess(
                TARGET,
                pattern,
                tick,
                copies,
                true,
                false);
    }

    private static final class EmptyPattern implements IPatternDetails {
        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of();
        }

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("third-party equality must not run");
        }

        @Override
        public int hashCode() {
            return 31;
        }
    }
}
