package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ExportTransferState;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.IOSpeedMode;
import com.moakiee.ae2lt.logic.TransferPollSchedule;

/** Exercise production timing against finite machines; delays themselves are not the oracle. */
class OverloadedInterfaceTransferCadenceTest {
    @Test
    void fillingFiniteMachinesPreservesProductionWithBoundedVisits() {
        for (var mode : IOSpeedMode.values()) {
            for (var model : new int[][] {{512, 512, 1}, {2048, 512, 1}, {512, 512, 5},
                    {2048, 512, 5}, {576, 9, 20}, {16384, 1, 1}}) {
                var result = run(model[0], model[1], model[2], mode, false);
                var old = run(model[0], model[1], model[2], mode, true);
                assertTrue(result.minimumThroughput >= 0.95, "idle machine: " + result);
                long idealFills = (result.possible + model[0] - 1) / model[0];
                // At most two half-capacity refills plus two rejection probes.
                // Very deep reservoirs retain the existing mode's maximum wait.
                long maxWait = mode == IOSpeedMode.FAST ? 20 : 80;
                // Independently require at most four probes per 100-tick
                // adaptation horizon, or the mode's stricter maximum wait.
                long budget = Math.max(4 * idealFills, Math.max(4 * 4500 / 100, 4500 / maxWait)) + 2;
                assertTrue(result.visits <= budget, "excessive visits: " + result + ", budget=" + budget);
                System.out.printf("interface-refill mode=%s capacity=%d rate=%d/%dt min100=%.2f%% visits(old/new)=%d/%d%n",
                        mode, model[0], model[1], model[2], 100 * result.minimumThroughput, old.visits, result.visits);
                if (model[0] == 2048 && model[2] == 1) {
                    assertTrue(result.visits < old.visits, "amount learning did not batch plentiful stock");
                }
            }
        }
    }

    @Test
    void changesInConsumptionRecoverWithoutLeavingMachinesIdle() {
        for (var mode : IOSpeedMode.values()) {
            int[] amounts = {9, 512, 10, 2048, 512};
            int[] periods = {20, 1, 1, 10, 5};
            var transfer = new ExportTransferState();
            long stock = 0, windowProcessed = 0, windowPossible = 0;
            for (int tick = 0; tick < 2500; tick++) {
                int stage = tick / 500;
                if (tick % periods[stage] == 0) {
                    long consumed = Math.min(stock, amounts[stage]);
                    stock -= consumed;
                    if (tick % 500 >= 100) {
                        windowProcessed += consumed;
                        windowPossible += amounts[stage];
                    }
                }
                if (tick >= transfer.untilTick) {
                    long accepted = 2048 - stock;
                    if (accepted > 0) {
                        stock += accepted;
                        transfer.accepted(tick, accepted, false, mode);
                    } else {
                        transfer.rejected(tick, mode);
                    }
                }
                if (tick % 500 >= 100 && tick % 100 == 99) {
                    assertTrue(windowProcessed * 100 >= windowPossible * 80,
                            "speed-change throughput: mode=" + mode + ", stage=" + stage
                                    + ", processed=" + windowProcessed + "/" + windowPossible);
                    windowProcessed = windowPossible = 0;
                }
            }
        }
    }

    @Test
    void configuredQuantityLimitDoesNotBecomeAPhysicalCapacityEstimate() {
        var transfer = new ExportTransferState();
        long stock = 0;
        for (int tick = 0; tick < 1000; tick++) {
            if (tick > 0) {
                assertEquals(8, stock, "configuration-limited refill left its processing opportunity idle");
                stock -= 8;
            }
            assertTrue(tick >= transfer.untilTick);
            long accepted = Math.min(8, 4096 - stock);
            stock += accepted;
            transfer.accepted(tick, accepted, true, IOSpeedMode.NORMAL);
        }
    }

    @Test
    void sourceAndPowerStarvationDoNotLeaveTargetRejectionHistory() {
        for (var mode : IOSpeedMode.values()) {
            var transfer = new ExportTransferState();
            long tick = 0;
            for (int attempt = 0; attempt < 30; attempt++) {
                transfer.rejected(tick, mode);
                tick = transfer.untilTick;
            }
            // This is the production path for both zero ME stock and zero affordable energy.
            transfer.unavailable(tick, mode);
            tick = transfer.untilTick;
            transfer.accepted(tick, 64, true, mode);
            assertEquals(tick + 1, transfer.untilTick, "restocking inherited a stale full-target wait");
            for (int i = 0; i < 100; i++) {
                tick = transfer.untilTick;
                transfer.accepted(tick, 64, true, mode);
                assertEquals(tick + 1, transfer.untilTick);
            }
        }
    }

    @Test
    void longSourceShortageHasBoundedPollingAndRecoversOnFirstSuccessfulFill() {
        var transfer = new ExportTransferState();
        long tick = 0;
        int visits = 0;
        while (tick < 10000) {
            transfer.unavailable(tick, IOSpeedMode.FAST);
            assertTrue(transfer.untilTick > tick && transfer.untilTick <= tick + 20);
            tick = transfer.untilTick;
            visits++;
        }
        assertTrue(visits <= 510);
        transfer.accepted(tick, 128, false, IOSpeedMode.FAST);
        assertEquals(tick + 1, transfer.untilTick);
    }

    @Test
    void independentKeysCannotDelayOneAnother() {
        var full = new ExportTransferState();
        var flowing = new ExportTransferState();
        for (long tick = 0; tick < 300; tick++) {
            if (tick >= full.untilTick) full.rejected(tick, IOSpeedMode.FAST);
            assertTrue(tick >= flowing.untilTick);
            flowing.accepted(tick, 64, false, IOSpeedMode.FAST);
        }
    }

    private static Result run(int capacity, int amount, int period, IOSpeedMode mode, boolean old) {
        var transfer = new ExportTransferState();
        var polling = new TransferPollSchedule();
        long due = 0, stock = 0, visits = 0, possible = 0, processed = 0;
        long windowProcessed = 0, windowPossible = 0;
        double minimum = 1;
        for (int tick = 0; tick < 5000; tick++) {
            if (tick % period == 0) {
                long consumed = Math.min(stock, amount);
                stock -= consumed;
                if (tick >= 500) {
                    possible += amount;
                    processed += consumed;
                    windowPossible += amount;
                    windowProcessed += consumed;
                }
            }
            if (tick >= due) {
                if (tick >= 500) visits++;
                long accepted = capacity - stock;
                if (accepted > 0) {
                    stock += accepted;
                    if (old) due = tick + polling.success(tick);
                    else transfer.accepted(tick, accepted, false, mode);
                } else {
                    if (old) due = tick + polling.failure(tick, mode == IOSpeedMode.FAST ? 20 : 80);
                    else transfer.rejected(tick, mode);
                }
                if (!old) due = transfer.untilTick;
            }
            if (tick >= 500 && tick % 100 == 99) {
                minimum = Math.min(minimum, (double) windowProcessed / windowPossible);
                windowProcessed = windowPossible = 0;
            }
        }
        return new Result(processed, possible, visits, minimum);
    }

    private record Result(long processed, long possible, long visits, double minimumThroughput) {}
}
