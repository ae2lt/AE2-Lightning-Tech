package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DispatchFairnessSchedulerTest {

    private static final String PATTERN = "pattern";
    private static final List<String> FOUR_TARGETS =
            List.of("a", "b", "c", "d");

    @Test
    void allowanceCountsOwnedBatchCopiesRatherThanPushCalls() {
        var scheduler = new DispatchFairnessScheduler<String, String>();

        dispatchFullPass(scheduler, FOUR_TARGETS, 0L);
        assertCounts(scheduler, 0L, 1L, 1L, 1L, 1L);

        dispatchFullPass(scheduler, FOUR_TARGETS, 1L);
        assertCounts(scheduler, 1L, 2L, 2L, 2L, 2L);

        try (var pass = scheduler.beginPass(
                PATTERN, FOUR_TARGETS, 1L, 2L)) {
            for (int i = 0; i < 2; i++) {
                var target = pass.poll();
                assertEquals(2L, pass.allowance(target));
                pass.success(target, 2L);
            }
        }

        assertEquals(2L, scheduler.minimumActiveDispatchCount(PATTERN, 2L));
        assertEquals(4L, scheduler.maximumActiveDispatchCount(PATTERN, 2L));
    }

    @Test
    void onePassNeverLeasesTheSameMachineTwice() {
        var scheduler = new DispatchFairnessScheduler<String, String>();

        try (var pass = scheduler.beginPass(
                PATTERN, FOUR_TARGETS, 1L, 0L)) {
            for (int i = 0; i < FOUR_TARGETS.size(); i++) {
                var target = pass.poll();
                pass.success(target, 1L);
            }
            assertEquals(null, pass.poll());
        }
    }

    @Test
    void successfulCopiesCannotExceedTheLeasedAllowance() {
        var scheduler = new DispatchFairnessScheduler<String, String>();

        try (var pass = scheduler.beginPass(
                PATTERN, FOUR_TARGETS, 1L, 0L)) {
            var target = pass.poll();
            assertEquals(1L, pass.allowance(target));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> pass.success(target, 2L));
        }
    }

    @Test
    void cooledTargetIsExcludedAndReturnsAtTheActiveAverage() {
        var scheduler = new DispatchFairnessScheduler<String, String>();
        var targets = List.of("fast", "slow");

        dispatchFullPass(scheduler, targets, 0L);
        dispatchFullPass(scheduler, targets, 1L);

        try (var pass = scheduler.beginPass(PATTERN, targets, 1L, 2L)) {
            var first = pass.poll();
            var second = pass.poll();
            if ("slow".equals(first)) {
                pass.cooldown(first, 10L);
                pass.success(second, pass.allowance(second));
            } else {
                pass.success(first, pass.allowance(first));
                pass.cooldown(second, 10L);
            }
        }

        assertEquals(1, scheduler.activeTargetCount(PATTERN, 9L));
        long activeCount = scheduler.maximumActiveDispatchCount(PATTERN, 9L);

        try (var ignored = scheduler.beginPass(PATTERN, targets, 1L, 10L)) {
            assertEquals(2, ignored.activeTargetsAtStart());
        }
        assertEquals(
                activeCount,
                scheduler.dispatchCount(PATTERN, "slow", 10L));
    }

    @Test
    void everyAcceptedAllocationKeepsActiveMaximumWithinTwiceMinimum() {
        var scheduler = new DispatchFairnessScheduler<Integer, String>();
        var targets = java.util.stream.IntStream.range(0, 32)
                .boxed()
                .toList();

        for (long tick = 0L; tick < 100L; tick++) {
            long remaining = 1L + (tick * 37L) % 300L;
            try (var pass = scheduler.beginPass(
                    PATTERN, targets, 1L, tick)) {
                Integer target;
                while (remaining > 0L && (target = pass.poll()) != null) {
                    long accepted = Math.min(remaining, pass.allowance(target));
                    if (accepted > 0L) {
                        pass.success(target, accepted);
                        remaining -= accepted;
                    }
                    assertActiveRatio(scheduler, tick);
                }
            }
            assertActiveRatio(scheduler, tick);
        }
    }

    @Test
    void dispatchCopiesExpireAfterOneHundredTicks() {
        var scheduler = new DispatchFairnessScheduler<String, String>();

        dispatchFullPass(scheduler, FOUR_TARGETS, 99L);
        assertEquals(1L, scheduler.maximumActiveDispatchCount(PATTERN, 99L));
        assertEquals(1L, scheduler.maximumActiveDispatchCount(PATTERN, 198L));
        assertEquals(0L, scheduler.maximumActiveDispatchCount(PATTERN, 199L));
        assertEquals(0L, scheduler.minimumActiveDispatchCount(PATTERN, 199L));
    }

    @Test
    void oneProviderBatchPerTickSustainsOneHundredParallelMachines() {
        assertSustainedThroughput(128L);
        assertSustainedThroughput(256L);
    }

    private static <T> void assertActiveRatio(
            DispatchFairnessScheduler<T, String> scheduler,
            long tick) {
        long minimum = scheduler.minimumActiveDispatchCount(PATTERN, tick);
        long maximum = scheduler.maximumActiveDispatchCount(PATTERN, tick);
        assertTrue(maximum <= 2L * Math.max(1L, minimum),
                () -> "active dispatch ratio exceeded 2:1: "
                        + minimum + ".." + maximum);
    }

    private static void dispatchFullPass(
            DispatchFairnessScheduler<String, String> scheduler,
            List<String> targets,
            long tick) {
        try (var pass = scheduler.beginPass(PATTERN, targets, 1L, tick)) {
            String target;
            while ((target = pass.poll()) != null) {
                long allowance = pass.allowance(target);
                pass.success(target, allowance);
            }
        }
    }

    private static void assertCounts(
            DispatchFairnessScheduler<String, String> scheduler,
            long tick,
            long a,
            long b,
            long c,
            long d) {
        assertEquals(a, scheduler.dispatchCount(PATTERN, "a", tick));
        assertEquals(b, scheduler.dispatchCount(PATTERN, "b", tick));
        assertEquals(c, scheduler.dispatchCount(PATTERN, "c", tick));
        assertEquals(d, scheduler.dispatchCount(PATTERN, "d", tick));
    }

    private static void assertSustainedThroughput(long recipesPerTick) {
        int machines = 100;
        long capacity = 1_000L;
        var scheduler = new DispatchFairnessScheduler<Integer, String>();
        var targets = java.util.stream.IntStream.range(0, machines)
                .boxed()
                .toList();
        long[] stored = new long[machines];

        long tick = 0L;
        while (tick < 32L
                && java.util.Arrays.stream(stored).anyMatch(value -> value < capacity)) {
            long free = java.util.Arrays.stream(stored)
                    .map(value -> capacity - value)
                    .sum();
            dispatchCopies(scheduler, targets, stored, capacity, free, tick);
            tick++;
        }
        for (long value : stored) {
            assertEquals(capacity, value);
        }

        for (int sustainedTick = 0; sustainedTick < 160; sustainedTick++, tick++) {
            for (int machine = 0; machine < machines; machine++) {
                stored[machine] -= recipesPerTick;
            }
            long leftover = dispatchCopies(
                    scheduler,
                    targets,
                    stored,
                    capacity,
                    recipesPerTick * machines,
                    tick);
            assertEquals(0L, leftover);
            for (long value : stored) {
                assertEquals(capacity, value);
            }
            assertActiveRatio(scheduler, tick);
        }
    }

    private static long dispatchCopies(
            DispatchFairnessScheduler<Integer, String> scheduler,
            List<Integer> targets,
            long[] stored,
            long capacity,
            long requested,
            long tick) {
        long remaining = requested;
        try (var pass = scheduler.beginPass(PATTERN, targets, 1L, tick)) {
            Integer target;
            while (remaining > 0L && (target = pass.poll()) != null) {
                long requestedForTarget = Math.min(
                        remaining, pass.allowance(target));
                long accepted = safeRampAccepted(
                        requestedForTarget, capacity - stored[target]);
                if (accepted <= 0L) {
                    continue;
                }
                pass.success(target, accepted);
                stored[target] += accepted;
                remaining -= accepted;
            }
        }
        return remaining;
    }

    private static long safeRampAccepted(long requested, long freeCapacity) {
        long accepted = 0L;
        long fullCredit = 0L;
        while (accepted < requested) {
            long chunk = Math.min(
                    fullCredit <= 0L ? 1L : fullCredit,
                    requested - accepted);
            if (chunk > freeCapacity - accepted) {
                break;
            }
            accepted += chunk;
            fullCredit = Long.MAX_VALUE - fullCredit < chunk
                    ? Long.MAX_VALUE
                    : fullCredit + chunk;
        }
        return accepted;
    }
}
