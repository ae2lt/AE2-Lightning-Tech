package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

/** Cadence contracts. End-to-end throughput and physical-call budgets live in
 * AdaptiveBatchDispatchStressTest, rather than prescribing an obsolete sequence of delays. */
class WirelessBatchCadenceTest {
    private static final String TARGET = "target";
    private final IPatternDetails pattern = new EmptyPattern();

    @Test
    void continuouslyAcceptedRefillsRemainReadyEveryTick() {
        var cadence = new WirelessBatchCadence<String>();
        for (int tick = 0; tick < 200; tick++) {
            assertEquals(1, success(cadence, tick, 8));
        }
    }

    @Test
    void rejectedGrowthDoesNotTurnShortRecoveryIntoLongIdle() {
        var cadence = new WirelessBatchCadence<String>();
        success(cadence, 0, 8);
        cadence.recordFailure(TARGET, pattern, 1);
        int delay = success(cadence, 5, 8);
        assertTrue(delay >= 1 && delay <= 5,
                "five-tick recovery produced a longer wait: " + delay);
    }

    @Test
    void persistentRejectionBacksOffWithoutStoppingRetries() {
        for (int chunk : new int[] {8, 32, 128}) {
            var cadence = new WirelessBatchCadence<String>();
            success(cadence, 0, chunk);
            long tick = 1;
            int visits = 0;
            while (tick < 1000) {
                int delay = cadence.recordFailure(TARGET, pattern, tick);
                assertBounded(delay);
                tick += delay;
                visits++;
            }
            assertTrue(visits <= 20, "full target was polled too often: " + visits);
        }
    }

    @Test
    void neverAcceptingTargetAlsoHasBoundedPollingCost() {
        var cadence = new WirelessBatchCadence<String>();
        long tick = 0;
        int visits = 0;
        while (tick < 1000) {
            int delay = cadence.recordFailure(TARGET, pattern, tick);
            assertBounded(delay);
            tick += delay;
            visits++;
        }
        assertTrue(visits <= 20);
    }

    @Test
    void stableFiveTickMachineKeepsProcessingWithBoundedCalls() {
        var cadence = new WirelessBatchCadence<String>();
        long due = 0;
        int stock = 0, processed = 0, possible = 0, visits = 0;
        for (int tick = 0; tick < 2000; tick++) {
            if (tick % 5 == 0) {
                if (tick >= 100) { processed += stock; possible += 8; }
                stock = 0;
            }
            if (tick >= due) {
                if (tick >= 100) visits++;
                int delay;
                if (stock == 0) {
                    stock = 8;
                    delay = success(cadence, tick, 8);
                } else {
                    delay = cadence.recordFailure(TARGET, pattern, tick);
                }
                assertBounded(delay);
                due = tick + delay;
            }
        }
        assertTrue(processed * 100L >= possible * 95L,
                "processing=" + processed + "/" + possible);
        assertTrue(visits <= 2 * (1900 / 5), "visits=" + visits);
    }

    @Test
    void fourEqualPrefixesEnableSingleChunkWithoutChangingOwnership() {
        var cadence = new WirelessBatchCadence<String>();
        for (int sample = 1; sample <= 4; sample++) {
            prefix(cadence, sample, 8);
            assertEquals(sample == 4, cadence.usesSingleChunkRefill(TARGET, pattern));
        }
        assertBounded(success(cadence, 5, 8));
        assertTrue(cadence.usesSingleChunkRefill(TARGET, pattern));
    }

    @Test
    void physicalPrefixEvidenceDoesNotRequireEqualProcessingPeriods() {
        var cadence = new WirelessBatchCadence<String>();
        for (int tick : new int[] {1, 3, 4, 6, 7, 9}) prefix(cadence, tick, 8);
        // This hint concerns the accepted physical amount, not a fixed processing period.
        // Variable-rate utilization and call overhead are checked by the R stress model.
        assertTrue(cadence.usesSingleChunkRefill(TARGET, pattern));
    }

    @Test
    void changedAcceptedPrefixMustBeLearnedAgain() {
        var cadence = new WirelessBatchCadence<String>();
        for (int tick = 1; tick <= 4; tick++) prefix(cadence, tick, 8);
        prefix(cadence, 5, 16);
        assertFalse(cadence.usesSingleChunkRefill(TARGET, pattern));
    }

    @Test
    void growthEvidenceReopensSingleChunkRefill() {
        var cadence = new WirelessBatchCadence<String>();
        for (int tick = 1; tick <= 4; tick++) prefix(cadence, tick, 8);
        cadence.recordSuccess(TARGET, pattern, 5, 16, false, ProviderTarget.BaselineStatus.GROWTH_COMPLETE);
        assertFalse(cadence.usesSingleChunkRefill(TARGET, pattern));
    }

    @Test
    void idleHistoryAndClockRollbackDiscardOldCadence() {
        for (long resetTick : new long[] {200, 0}) {
            var cadence = new WirelessBatchCadence<String>();
            for (int tick = 10; tick <= 40; tick += 10) prefix(cadence, tick, 8);
            assertEquals(1, success(cadence, resetTick, 8));
            assertFalse(cadence.usesSingleChunkRefill(TARGET, pattern));
        }
    }

    @Test
    void rejectionOfOnePatternDoesNotDelayAnotherPatternOrTarget() {
        var cadence = new WirelessBatchCadence<String>();
        var otherPattern = new EmptyPattern();
        long tick = 0;
        for (int attempt = 0; attempt < 12; attempt++) {
            tick += cadence.recordFailure(TARGET, pattern, tick);
        }
        assertEquals(1, cadence.recordSuccess(TARGET, otherPattern, tick, 8, true));
        assertEquals(1, cadence.recordSuccess("other", pattern, tick, 8, true));
    }

    @Test
    void removingOneTargetPreservesOtherTargetsLearning() {
        var cadence = new WirelessBatchCadence<String>();
        for (int tick = 1; tick <= 4; tick++) {
            prefix(cadence, tick, 8);
            cadence.recordSuccess("other", pattern, tick, 8, false, ProviderTarget.BaselineStatus.PREFIX_COMPLETE);
        }
        cadence.removeTarget(TARGET);
        assertFalse(cadence.usesSingleChunkRefill(TARGET, pattern));
        assertTrue(cadence.usesSingleChunkRefill("other", pattern));
        cadence.clear();
        assertFalse(cadence.usesSingleChunkRefill("other", pattern));
    }

    @Test
    void extremeAmountsCannotOverflowRetryBounds() {
        var cadence = new WirelessBatchCadence<String>();
        assertBounded(success(cadence, 0, Long.MAX_VALUE));
        assertBounded(cadence.recordSuccess(TARGET, pattern, 99, 1, false));
        assertBounded(cadence.recordFailure(TARGET, pattern, 100));
    }

    @Test
    void zeroOwnedCopiesCannotBeRecordedAsSuccess() {
        var cadence = new WirelessBatchCadence<String>();
        assertThrows(IllegalArgumentException.class, () -> success(cadence, 0, 0));
    }

    private void prefix(WirelessBatchCadence<String> cadence, long tick, long copies) {
        assertBounded(cadence.recordSuccess(TARGET, pattern, tick, copies, false, ProviderTarget.BaselineStatus.PREFIX_COMPLETE));
    }
    private int success(WirelessBatchCadence<String> cadence, long tick, long copies) {
        return cadence.recordSuccess(TARGET, pattern, tick, copies, true);
    }
    private void assertBounded(int delay) {
        assertTrue(delay >= 1 && delay <= WirelessBatchCadence.MAX_COVERAGE_TICKS,
                "unbounded scheduling delay: " + delay);
    }
    private static final class EmptyPattern implements IPatternDetails {
        public AEItemKey getDefinition() { return null; }
        public IInput[] getInputs() { return new IInput[0]; }
        public GenericStack[] getOutputs() { return new GenericStack[0]; }
        public boolean equals(Object other) { throw new AssertionError("unexpected equality"); }
        public int hashCode() { return 31; }
    }
}
