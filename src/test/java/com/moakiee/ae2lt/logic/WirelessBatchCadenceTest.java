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

    @Test
    void stablePrefixFailuresConvergeToSingleChunkRefill() {
        var cadence = new WirelessBatchCadence<String>();
        for (long tick = 1L; tick <= 4L; tick++) {
            assertEquals(1, cadence.recordSuccess(
                    TARGET,
                    pattern,
                    tick,
                    8L,
                    false,
                    false,
                    false,
                    ProviderTarget.BaselineStatus.PREFIX_COMPLETE));
            assertFalse(cadence.usesSingleChunkRefill(TARGET, pattern));
        }

        assertEquals(1, cadence.recordSuccess(
                TARGET,
                pattern,
                5L,
                8L,
                false,
                false,
                false,
                ProviderTarget.BaselineStatus.PREFIX_COMPLETE));
        assertTrue(cadence.usesSingleChunkRefill(TARGET, pattern));

        assertEquals(1, cadence.recordSuccess(
                TARGET,
                pattern,
                6L,
                8L,
                true,
                false,
                false,
                ProviderTarget.BaselineStatus.NONE));
        assertTrue(cadence.usesSingleChunkRefill(TARGET, pattern));
    }

    @Test
    void unstablePrefixIntervalsDoNotLockSingleChunkRefill() {
        var cadence = new WirelessBatchCadence<String>();
        long tick = 0L;
        for (int sample = 0; sample < 10; sample++) {
            tick += sample % 2 == 0 ? 1L : 2L;
            cadence.recordSuccess(
                    TARGET,
                    pattern,
                    tick,
                    8L,
                    false,
                    false,
                    false,
                    ProviderTarget.BaselineStatus.PREFIX_COMPLETE);
        }

        assertFalse(cadence.usesSingleChunkRefill(TARGET, pattern));
    }

    @Test
    void singleChunkRefillProbesEarlierWithoutDiscardingItsInterval() {
        var cadence = cadenceWithFiveTickSingleChunkRefill();

        assertEquals(4, success(cadence, 35L, 8, false));
        assertTrue(cadence.isExploratoryAttempt(TARGET, pattern));

        assertEquals(1, cadence.recordFailure(
                TARGET, pattern, 39L, 8, true));
        assertTrue(cadence.usesSingleChunkRefill(TARGET, pattern));
        assertEquals(5, success(cadence, 40L, 8, false));
        assertFalse(cadence.isExploratoryAttempt(TARGET, pattern));
    }

    @Test
    void repeatedEarlyRejectionsSettleAStableSingleChunkCadence() {
        var cadence = cadenceWithFiveTickSingleChunkRefill();

        assertEquals(4, success(cadence, 35L, 8, false));
        assertEquals(1, cadence.recordFailure(
                TARGET, pattern, 39L, 8, true));
        assertEquals(5, success(cadence, 40L, 8, false));

        assertEquals(4, success(cadence, 45L, 8, false));
        assertEquals(1, cadence.recordFailure(
                TARGET, pattern, 49L, 8, true));
        assertEquals(5, success(cadence, 50L, 8, false));

        for (long tick = 55L; tick <= 75L; tick += 5L) {
            assertEquals(5, success(cadence, tick, 8, false));
            assertFalse(cadence.isExploratoryAttempt(TARGET, pattern));
        }
    }

    @Test
    void acceptedEarlyProbeShortensSingleChunkCadence() {
        var cadence = cadenceWithFiveTickSingleChunkRefill();

        assertEquals(4, success(cadence, 35L, 8, false));
        assertTrue(cadence.isExploratoryAttempt(TARGET, pattern));
        assertEquals(4, success(cadence, 39L, 8, true));
        assertFalse(cadence.isExploratoryAttempt(TARGET, pattern));
    }

    private WirelessBatchCadence<String>
            cadenceWithFiveTickSingleChunkRefill() {
        var cadence = new WirelessBatchCadence<String>();
        for (long tick = 5L; tick <= 30L; tick += 5L) {
            cadence.recordSuccess(
                    TARGET,
                    pattern,
                    tick,
                    8L,
                    false,
                    false,
                    false,
                    ProviderTarget.BaselineStatus.PREFIX_COMPLETE);
        }
        assertTrue(cadence.usesSingleChunkRefill(TARGET, pattern));
        return cadence;
    }

    private int success(
            WirelessBatchCadence<String> cadence,
            long tick,
            int copies) {
        return success(cadence, tick, copies, false);
    }

    private int success(
            WirelessBatchCadence<String> cadence,
            long tick,
            int copies,
            boolean exploratory) {
        return cadence.recordSuccess(
                TARGET,
                pattern,
                tick,
                copies,
                true,
                false,
                exploratory);
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
