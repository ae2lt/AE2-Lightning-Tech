package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TransferPollScheduleTest {
    @Test
    void saturatedMachinesKeepEveryProcessingOpportunityWithBoundedPolling() {
        for (int period : new int[] {1, 2, 5, 6, 10, 20, 60}) {
            for (int idleLimit : new int[] {20, 80}) {
                var schedule = new TransferPollSchedule();
                long due = 0;
                int stock = 0;
                long actual = 0, possible = 0, attempts = 0;
                for (int tick = 0; tick < 5_000; tick++) {
                    if (tick % period == 0) {
                        if (tick >= 200) {
                            actual += stock;
                            possible += 64;
                        }
                        stock = 0;
                    }
                    if (tick >= due) {
                        if (tick >= 200) attempts++;
                        if (stock == 0) {
                            stock = 64;
                            due = tick + schedule.success(tick);
                        } else {
                            due = tick + schedule.failure(tick, idleLimit);
                        }
                    }
                }
                assertEquals(possible, actual, "idle machine, period=" + period);
                assertTrue(attempts <= 2 * ((5_000 - 200) / period + 1),
                        "polling exceeded two attempts per processing cycle: " + attempts);
            }
        }
    }

    @Test
    void speedChangesRecoverWithoutRetainingTheOldSlowRate() {
        var schedule = new TransferPollSchedule();
        long due = 0;
        int stock = 0;
        int[] periods = {20, 1, 6, 60, 5, 10, 2};
        int[] actual = new int[periods.length];
        int[] possible = new int[periods.length];
        for (int tick = 0; tick < periods.length * 500; tick++) {
            int stage = tick / 500;
            if (tick % periods[stage] == 0) {
                if (tick % 500 >= 100) {
                    actual[stage] += stock;
                    possible[stage] += 64;
                }
                stock = 0;
            }
            if (tick >= due) {
                if (stock == 0) {
                    stock = 64;
                    due = tick + schedule.success(tick);
                } else {
                    due = tick + schedule.failure(tick, 80);
                }
            }
        }
        assertArrayEquals(possible, actual);
    }

    @Test
    void longIdleBacksOffButPollingNeverStops() {
        for (int maximum : new int[] {20, 80}) {
            var schedule = new TransferPollSchedule();
            long tick = 0;
            int attempts = 0;
            while (tick < 10_000) {
                int delay = schedule.failure(tick, maximum);
                assertTrue(delay >= 1 && delay <= maximum);
                tick += delay;
                attempts++;
            }
            assertTrue(attempts <= 10_000 / maximum + 10);
            assertEquals(1, schedule.success(tick));
            assertEquals(1, schedule.success(tick + 1));
        }
    }

    @Test
    void resetAndClockRollbackDiscardStaleRate() {
        var schedule = new TransferPollSchedule();
        schedule.success(0);
        schedule.failure(1, 80);
        assertEquals(10, schedule.success(20));
        assertEquals(1, schedule.success(5));
        schedule.reset();
        assertEquals(1, schedule.success(1000));
    }
}
