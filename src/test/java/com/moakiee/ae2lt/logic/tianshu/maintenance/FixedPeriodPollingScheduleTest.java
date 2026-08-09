package com.moakiee.ae2lt.logic.tianshu.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FixedPeriodPollingScheduleTest {
    @Test
    void oneRuleIsCheckedOncePerSixtyFourTicks() {
        var schedule = new FixedPeriodPollingSchedule(64);
        for (int tick = 1; tick < 64; tick++) {
            assertEquals(0, schedule.checksThisTick(1));
        }
        assertEquals(1, schedule.checksThisTick(1));
    }

    @Test
    void maximumRuleCountProducesThirtyTwoChecksPerTick() {
        var schedule = new FixedPeriodPollingSchedule(64);
        for (int tick = 0; tick < 64; tick++) {
            assertEquals(32, schedule.checksThisTick(2048));
        }
    }

    @Test
    void smallRuleSetsReducePerTickWorkButCompleteOneCycle() {
        var schedule = new FixedPeriodPollingSchedule(64);
        int total = 0;
        int peak = 0;
        for (int tick = 0; tick < 64; tick++) {
            int checks = schedule.checksThisTick(10);
            total += checks;
            peak = Math.max(peak, checks);
        }
        assertEquals(10, total);
        assertTrue(peak <= 1);
    }

    @Test
    void cursorVisitsEveryRuleExactlyOncePerCycle() {
        var schedule = new FixedPeriodPollingSchedule(64);
        boolean[] visited = new boolean[10];
        for (int tick = 0; tick < 64; tick++) {
            int checks = schedule.checksThisTick(visited.length);
            for (int i = 0; i < checks; i++) {
                int index = schedule.nextIndex(visited.length);
                assertEquals(false, visited[index]);
                visited[index] = true;
            }
        }
        for (boolean value : visited) {
            assertTrue(value);
        }
    }
}
