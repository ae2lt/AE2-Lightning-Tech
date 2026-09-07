package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.IntPredicate;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.CooldownTracker;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.IOSpeedMode;
import com.moakiee.ae2lt.logic.TransferPollSchedule;

/** Exercise the actual importer callback contract, including its next-tick drain check. */
class OverloadedInterfaceImportRecoveryTest {
    @Test
    void fixedProductionKeepsItsLongTermVisitCost() {
        for (var mode : IOSpeedMode.values()) {
            for (int period : new int[] {1, 2, 5, 6, 10, 20, 60, 99, 100, 200}) {
                for (int phase = 0; phase < 20; phase++) {
                    int offset = phase;
                    IntPredicate production = tick -> tick >= offset && (tick - offset) % period == 0;
                    var before = run(false, mode, production, 10_000, 1_000);
                    var after = run(true, mode, production, 10_000, 1_000);
                    String label = mode + " period=" + period + " phase=" + phase;
                    assertEquals(0, after.blocked, label);
                    // A different polling phase can straddle either sample boundary.
                    assertTrue(after.visits <= before.visits + 2, label + ": " + before + " -> " + after);
                    assertTrue(after.completed >= before.completed - 1, label);
                }
            }
        }
    }

    @Test
    void longIdleAndFirstDetectionAreUnchanged() {
        for (var mode : IOSpeedMode.values()) {
            assertEquals(run(false, mode, tick -> false, 20_000, 0),
                    run(true, mode, tick -> false, 20_000, 0));
            for (int phase = 0; phase < 80; phase++) {
                int arrival = 1_500 + phase;
                IntPredicate production = tick -> tick == arrival;
                assertEquals(run(false, mode, production, 2_000, 0),
                        run(true, mode, production, 2_000, 0), mode + " arrival=" + arrival);
            }
        }
    }

    @Test
    void restartedSlowMachinesKeepTheirLongTermVisitCost() {
        for (int period : new int[] {1, 5, 20, 60}) {
            for (int phase = 0; phase < 60; phase++) {
                int restart = 200 + phase;
                IntPredicate production = tick -> tick < 40 || tick >= restart && (tick - restart) % period == 0;
                var before = run(false, IOSpeedMode.FAST, production, 10_000, 1_000);
                var after = run(true, IOSpeedMode.FAST, production, 10_000, 1_000);
                String label = "period=" + period + " restart=" + restart;
                assertTrue(after.blocked <= before.blocked, label + ": " + before + " -> " + after);
                assertTrue(after.visits <= before.visits + 2, label + ": " + before + " -> " + after);
            }
        }
    }

    @Test
    void oneLongGapCannotSetTheNextWaitButRecurringSlowOutputCan() {
        var poller = new CooldownTracker();
        poller.reset(IOSpeedMode.FAST);
        poller.onSuccess(0, IOSpeedMode.FAST);
        poller.onSuccess(1, IOSpeedMode.FAST);
        poller.onSuccess(2, IOSpeedMode.FAST);
        poller.onFail(3, IOSpeedMode.FAST);
        poller.onSuccess(82, IOSpeedMode.FAST);
        poller.onFail(83, IOSpeedMode.FAST);
        assertEquals(84, poller.cooldownUntil(), "a pause is not evidence of a new recurring cadence");

        poller.reset(IOSpeedMode.FAST);
        poller.onSuccess(0, IOSpeedMode.FAST);
        poller.onFail(1, IOSpeedMode.FAST);
        poller.onSuccess(60, IOSpeedMode.FAST);
        poller.onFail(61, IOSpeedMode.FAST);
        poller.onSuccess(120, IOSpeedMode.FAST);
        poller.onFail(121, IOSpeedMode.FAST);
        assertEquals(180, poller.cooldownUntil(), "recurring slow output must still permit cheap polling");
    }

    @Test
    void slowJitterAndIsolatedPairsDoNotAddPermanentPolling() {
        for (int[] intervals : new int[][] {{19, 20, 21}, {59, 60, 61}, {5, 6}, {1, 60}}) {
            for (int phase = 0; phase < 20; phase++) {
                boolean[] events = new boolean[20_000];
                int tick = phase, index = 0;
                while (tick < events.length) {
                    events[tick] = true;
                    tick += intervals[index++ % intervals.length];
                }
                IntPredicate production = time -> events[time];
                var before = run(false, IOSpeedMode.FAST, production, events.length, 1_000);
                var after = run(true, IOSpeedMode.FAST, production, events.length, 1_000);
                String label = java.util.Arrays.toString(intervals) + " phase=" + phase + ": " + before + " -> " + after;
                // The two sample edges can include different drain/empty pairs.
                assertTrue(after.visits <= before.visits + 4, label);
                assertTrue(after.completed >= before.completed - 1, label);
                assertTrue(after.maxWait <= before.maxWait, label);
                assertEquals(0, after.blocked);
            }
        }
    }

    @Test
    void recoveryServesShortBurstsAndReturnsToFullSpeed() {
        IntPredicate production = tick -> tick < 40 || tick == 120 || tick >= 140 && tick < 144
                || tick >= 180 && tick <= 240 && tick % 20 == 0 || tick >= 260 && tick < 340;
        var before = run(false, IOSpeedMode.FAST, production, 400, 100);
        var after = run(true, IOSpeedMode.FAST, production, 400, 100);
        assertTrue(before.maxWait >= 60, "fixture must reproduce the stale-period pause");
        assertTrue(after.maxWait <= 5, after.toString());
        assertTrue(after.blocked < before.blocked, before + " -> " + after);
        var hot = run(true, IOSpeedMode.FAST, production, 340, 266);
        assertEquals(0, hot.blocked, hot.toString());
        System.out.println("Import recovery old/new: " + before + " -> " + after);
    }

    @Test
    void modeChangesDiscardOldObservations() {
        var poller = new CooldownTracker();
        poller.reset(IOSpeedMode.FAST);
        poller.onSuccess(0, IOSpeedMode.FAST);
        poller.onFail(1, IOSpeedMode.FAST);
        poller.onSuccess(60, IOSpeedMode.FAST);
        poller.onFail(61, IOSpeedMode.FAST);
        poller.onSuccess(120, IOSpeedMode.FAST);
        poller.onFail(121, IOSpeedMode.NORMAL);
        assertEquals(130, poller.cooldownUntil());
        poller.onSuccess(130, IOSpeedMode.NORMAL);
        poller.onFail(131, IOSpeedMode.NORMAL);
        assertEquals(132, poller.cooldownUntil());
    }

    private static Result run(boolean fixed, IOSpeedMode mode, IntPredicate production, int end, int sampleStart) {
        var poller = new Poller(fixed, mode);
        boolean stock = false;
        int producedAt = 0;
        long planned = 0, blocked = 0, visits = 0, completed = 0;
        int maxWait = 0;
        for (int tick = 0; tick < end; tick++) {
            if (production.test(tick)) {
                if (tick >= sampleStart) planned++;
                if (stock) {
                    if (tick >= sampleStart) blocked++;
                } else {
                    stock = true;
                    producedAt = tick;
                }
            }
            if (poller.due(tick)) {
                if (tick >= sampleStart) visits++;
                if (stock && tick >= sampleStart) {
                    completed++;
                    maxWait = Math.max(maxWait, tick - producedAt);
                }
                poller.visit(tick, stock);
                stock = false;
            }
        }
        return new Result(planned, blocked, visits, completed, maxWait);
    }

    private record Result(long planned, long blocked, long visits, long completed, int maxWait) {}

    /** Frozen alpha.3 baseline: the shared helper's success delay was intentionally ignored by import. */
    private static final class Poller {
        private final CooldownTracker fixed;
        private final TransferPollSchedule baseline = new TransferPollSchedule();
        private final IOSpeedMode mode;
        private long next;

        Poller(boolean useFix, IOSpeedMode mode) {
            fixed = useFix ? new CooldownTracker() : null;
            this.mode = mode;
            if (fixed != null) fixed.reset(mode);
        }

        boolean due(long tick) { return tick >= (fixed == null ? next : fixed.cooldownUntil()); }

        void visit(long tick, boolean output) {
            if (fixed != null) {
                if (output) fixed.onSuccess(tick, mode);
                else fixed.onFail(tick, mode);
            } else if (output) {
                baseline.success(tick);
                next = tick + 1;
            } else {
                next = tick + baseline.failure(tick, mode == IOSpeedMode.FAST ? 20 : 80);
            }
        }
    }
}
