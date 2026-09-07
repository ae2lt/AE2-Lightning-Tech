package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessDispatchMode;

class ProviderDispatchTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void continuouslyBlockedTargetsBackOffAndResumeWithinTheExistingLimit(boolean fast, boolean batch) {
        var dispatch = new ProviderWirelessDispatch();
        var pattern = new ExplosiveEqualityPattern();
        var connections = List.of(connection(0), connection(1));
        var rejectedAt = new ArrayList<Long>();
        var recoveredAt = new ArrayList<Long>();
        var healthyCopies = new AtomicInteger();
        for (long tick = 0; tick < 240; tick++) {
            long now = tick;
            dispatch.prepare(connections, now, fast, WirelessDispatchMode.EVEN_DISTRIBUTION);
            ProviderWirelessDispatch.SingleAttempt attempt = target -> {
                if (target.equals(connections.getFirst())) {
                    if (now < 200) {
                        rejectedAt.add(now);
                        return WirelessPushOutcome.SOFT_FAIL;
                    }
                    recoveredAt.add(now);
                } else {
                    healthyCopies.incrementAndGet();
                }
                return WirelessPushOutcome.SUCCESS;
            };
            if (batch) {
                dispatch.dispatchBatch(WirelessDispatchMode.EVEN_DISTRIBUTION,
                        pattern, 2, now, fast,
                        (target, allowance, exploratory, preserve) -> {
                            var outcome = attempt.push(target);
                            return outcome == WirelessPushOutcome.SUCCESS
                                    ? successfulBatch(1) : rejectedBatch(0);
                        }, ignored -> true, ignored -> { throw new AssertionError(); });
            } else {
                // Two calls permit both targets to work once a tick when available.
                for (int call = 0; call < 2; call++) {
                    dispatch.dispatchSingleCopy(WirelessDispatchMode.EVEN_DISTRIBUTION,
                            pattern, now, fast, 2, attempt, ignored -> true,
                            ignored -> { throw new AssertionError(); });
                }
            }
        }
        assertEquals(fast ? List.of(0L, 2L, 5L, 9L, 14L) : List.of(0L, 5L, 14L, 27L, 44L),
                rejectedAt.subList(0, 5));
        assertTrue(rejectedAt.size() <= (fast ? 25 : 12), "blocked calls: " + rejectedAt);
        assertTrue(!recoveredAt.isEmpty() && recoveredAt.getFirst() < 200 + (fast ? 10 : 40));
        assertTrue(healthyCopies.get() >= 240, "a blocked target must not idle the healthy machine");
    }

    @Test
    void rejectionHistoryResetsOnSuccessAndExpiresWhenUnused() {
        var dispatch = new ProviderWirelessDispatch();
        var target = connection(0);
        var pattern = new ExplosiveEqualityPattern();
        var other = new ExplosiveEqualityPattern();
        assertEquals(5, dispatch.recordRejection(target, pattern, 0, false));
        assertEquals(Long.MIN_VALUE, dispatch.retryAfter(target, pattern, 5));
        assertEquals(14, dispatch.recordRejection(target, pattern, 5, false));
        assertEquals(10, dispatch.recordRejection(target, other, 5, false));
        dispatch.recordSuccess(target, pattern, 6, true);
        assertEquals(8, dispatch.recordRejection(target, pattern, 7, false));
        assertEquals(205, dispatch.recordRejection(target, pattern, 200, false));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2, 5, 6, 10, 20, 60})
    void busyGatePollingKeepsSerialMachinesWorkingAtTheirRealCycle(int period) {
        for (boolean fast : new boolean[] {false, true}) {
            for (boolean batch : new boolean[] {false, true}) {
                simulateBusyGate(new int[] {period}, fast, batch);
            }
        }
    }

    @Test
    void busyGatePollingRelearnsFasterAndSlowerSerialMachines() {
        for (boolean fast : new boolean[] {false, true}) {
            for (boolean batch : new boolean[] {false, true}) {
                simulateBusyGate(new int[] {20, 1, 6, 60, 5, 10, 2}, fast, batch);
            }
        }
    }

    private static void simulateBusyGate(int[] periods, boolean fast, boolean batch) {
        var dispatch = new ProviderWirelessDispatch();
        var pattern = new ExplosiveEqualityPattern();
        var targets = List.of(connection(0));
        long[] finishAt = {-1};
        long[] completed = new long[periods.length];
        long[] calls = new long[periods.length];
        for (long tick = 0; tick < periods.length * 2000L; tick++) {
            int stage = (int) (tick / 2000);
            boolean measured = tick % 2000 >= 200;
            if (finishAt[0] == tick && measured) completed[stage]++;
            long now = tick;
            dispatch.prepare(targets, now, fast, WirelessDispatchMode.EVEN_DISTRIBUTION);
            ProviderWirelessDispatch.SingleAttempt attempt = target -> {
                if (measured) calls[stage]++;
                if (finishAt[0] > now) return WirelessPushOutcome.SOFT_FAIL;
                // Completion is relative to insertion, not a free-running clock.
                // A late retry really idles the machine and reduces throughput.
                finishAt[0] = now + periods[stage];
                return WirelessPushOutcome.SUCCESS;
            };
            if (batch) {
                dispatch.dispatchBatch(WirelessDispatchMode.EVEN_DISTRIBUTION,
                        pattern, 1, now, fast,
                        (target, allowance, exploratory, preserve) ->
                                attempt.push(target) == WirelessPushOutcome.SUCCESS
                                        ? successfulBatch(1) : rejectedBatch(0),
                        ignored -> true, ignored -> { throw new AssertionError(); });
            } else {
                dispatch.dispatchSingleCopy(WirelessDispatchMode.EVEN_DISTRIBUTION,
                        pattern, now, fast, 1, attempt, ignored -> true,
                        ignored -> { throw new AssertionError(); });
            }
        }
        for (int stage = 0; stage < periods.length; stage++) {
            long ideal = 1800 / periods[stage];
            String context = "period=" + periods[stage] + ", fast=" + fast + ", batch=" + batch;
            assertTrue(completed[stage] >= ideal * 95 / 100, context + ", completed=" + completed[stage]);
            assertTrue(calls[stage] <= 2 * (ideal + 1), context + ", calls=" + calls[stage]);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "1,1", "16,1", "256,1", "257,2", "512,2", "513,3",
            "2000,8", "4096,16", "4097,16", "10000,16"
    })
    void groupedBatchesUseOnlyNeededMachinesAndRetainPhysicalRamp(long copies, int expectedTargets) {
        var dispatch = new ProviderWirelessDispatch();
        var pattern = new ExplosiveEqualityPattern();
        var connections = java.util.stream.IntStream.range(0, 16)
                .mapToObj(ProviderDispatchTest::connection).toList();
        var owned = new ArrayList<Long>();
        var chunks = new ArrayList<List<Integer>>();
        dispatch.prepare(connections, 0L, false, WirelessDispatchMode.EVEN_DISTRIBUTION);

        long remaining = dispatch.dispatchBatch(
                WirelessDispatchMode.EVEN_DISTRIBUTION, pattern, copies, 0L, false, 256,
                (target, allowance, exploratory, preserveHistory) -> {
                    var physical = new ArrayList<Integer>();
                    var step = target.pushPatternStep(pattern, allowance, 0L, true, preserveHistory,
                            () -> false, count -> {
                                physical.add(count);
                                return new ProviderTarget.BatchChunk(count, true, false);
                            });
                    chunks.add(physical);
                    owned.add(step.ownedCopies());
                    return new ProviderWirelessDispatch.BatchAttemptResult(
                            step.ownedCopies(), step.attemptedCopies(), step.acceptedFullChunk(),
                            step.requestLimited(), step.baselineStatus(), WirelessPushOutcome.SUCCESS);
                }, ignored -> true, ignored -> { throw new AssertionError(); });

        assertEquals(0L, remaining);
        assertEquals(expectedTargets, owned.size());
        assertEquals(copies, owned.stream().mapToLong(Long::longValue).sum());
        long minimum = owned.stream().mapToLong(Long::longValue).min().orElseThrow();
        long maximum = owned.stream().mapToLong(Long::longValue).max().orElseThrow();
        assertTrue(maximum - minimum <= 1L);
        if (copies == 16L) {
            assertEquals(List.of(1, 1, 2, 4, 8), chunks.getFirst());
        }
        if (copies == 257L) {
            assertEquals(List.of(129L, 128L), owned);
        }
    }

    @Test
    void groupedTargetCountHandlesLongRequestsAndEmptyTopology() {
        assertEquals(16, ProviderWirelessDispatch.groupedTargetCount(Long.MAX_VALUE, 256, 16));
        assertEquals(16, ProviderWirelessDispatch.groupedTargetCount(Long.MAX_VALUE, Integer.MAX_VALUE, 16));
        assertEquals(0, ProviderWirelessDispatch.groupedTargetCount(0, 256, 16));
        assertEquals(0, ProviderWirelessDispatch.groupedTargetCount(16, 256, 0));
    }

    @Test
    void groupedRejectionsAllowBackupMachinesWithoutConsumingSuccessfulSlots() {
        var dispatch = new ProviderWirelessDispatch();
        var pattern = new ExplosiveEqualityPattern();
        var connections = java.util.stream.IntStream.range(0, 16)
                .mapToObj(ProviderDispatchTest::connection).toList();
        dispatch.prepare(connections, 0L, false, WirelessDispatchMode.EVEN_DISTRIBUTION);
        var visits = new AtomicInteger();
        var shares = new ArrayList<Long>();
        long remaining = dispatch.dispatchBatch(
                WirelessDispatchMode.EVEN_DISTRIBUTION, pattern, 257L, 0L, false, 256,
                (target, allowance, exploratory, preserve) -> {
                    if (visits.incrementAndGet() <= 3) return rejectedBatch(1);
                    shares.add(allowance);
                    return successfulBatch((int) allowance);
                }, ignored -> true, ignored -> { throw new AssertionError(); });
        assertEquals(0L, remaining);
        assertEquals(5, visits.get());
        assertEquals(List.of(129L, 128L), shares);
    }

    @Test
    void groupedPartialAcceptanceSpillsToBackupMachinesWithinOnePass() {
        var dispatch = new ProviderWirelessDispatch();
        var pattern = new ExplosiveEqualityPattern();
        var connections = java.util.stream.IntStream.range(0, 16)
                .mapToObj(ProviderDispatchTest::connection).toList();
        dispatch.prepare(connections, 0L, false, WirelessDispatchMode.EVEN_DISTRIBUTION);
        var visits = new AtomicInteger();
        long remaining = dispatch.dispatchBatch(
                WirelessDispatchMode.EVEN_DISTRIBUTION, pattern, 16L, 0L, false, 256,
                (target, allowance, exploratory, preserve) -> {
                    visits.incrementAndGet();
                    return new ProviderWirelessDispatch.BatchAttemptResult(Math.min(3L, allowance), 4, false, false,
                            ProviderTarget.BaselineStatus.NONE, WirelessPushOutcome.SUCCESS);
                }, ignored -> true, ignored -> { throw new AssertionError(); });
        assertEquals(0L, remaining);
        assertEquals(6, visits.get());
    }

    @Test
    void groupedGlobalAbortPreservesOwnershipAndStopsImmediately() {
        for (int owned : new int[] {0, 3}) {
            var dispatch = new ProviderWirelessDispatch();
            var pattern = new ExplosiveEqualityPattern();
            dispatch.prepare(List.of(connection(0), connection(1)), 0L, false,
                    WirelessDispatchMode.EVEN_DISTRIBUTION);
            var visits = new AtomicInteger();
            long remaining = dispatch.dispatchBatch(
                    WirelessDispatchMode.EVEN_DISTRIBUTION, pattern, 257L, 0L, false, 256,
                    (target, allowance, exploratory, preserve) -> {
                        visits.incrementAndGet();
                        return new ProviderWirelessDispatch.BatchAttemptResult(owned, 4, false, false,
                                ProviderTarget.BaselineStatus.NONE, WirelessPushOutcome.GLOBAL_ABORT);
                    }, ignored -> true, ignored -> { throw new AssertionError(); });
            assertEquals(257L - owned, remaining);
            assertEquals(1, visits.get());
        }
    }

    @Test
    void singleTargetBatchIgnoresMachineParallelism() {
        var dispatch = new ProviderWirelessDispatch();
        var pattern = new ExplosiveEqualityPattern();
        dispatch.prepare(List.of(connection(0), connection(1)), 0L, false,
                WirelessDispatchMode.SINGLE_TARGET);
        var shares = new ArrayList<Long>();
        long remaining = dispatch.dispatchBatch(
                WirelessDispatchMode.SINGLE_TARGET, pattern, 2000L, 0L, false, 256,
                (target, allowance, exploratory, preserve) -> {
                    shares.add(allowance);
                    return successfulBatch((int) allowance);
                }, ignored -> true, ignored -> { throw new AssertionError(); });
        assertEquals(0L, remaining);
        assertEquals(List.of(2000L), shares);
    }

    @Test
    void normalBatchVisitsAnotherTargetAfterOneRejects() {
        var dispatch = new ProviderNormalDispatch();
        var first = target(0);
        var second = target(1);
        var attempts = new AtomicInteger();

        long remaining = dispatch.dispatchBatch(
                List.of(first, second),
                1L,
                (target, maxCopies) -> attempts.getAndIncrement() == 0
                        ? new ProviderNormalDispatch.BatchAttemptResult(
                                0L, false, false)
                        : new ProviderNormalDispatch.BatchAttemptResult(
                                maxCopies, false, false));

        assertEquals(0L, remaining);
        assertEquals(2, attempts.get());
    }

    @Test
    void normalBatchEvenlySplitsAcrossAtMostSixTargets() {
        var dispatch = new ProviderNormalDispatch();
        var targets = java.util.stream.IntStream.range(0, 6)
                .mapToObj(ProviderDispatchTest::target)
                .toList();
        var shares = new ArrayList<Long>();

        long remaining = dispatch.dispatchBatch(
                targets,
                100L,
                (target, maxCopies) -> {
                    shares.add(maxCopies);
                    return new ProviderNormalDispatch.BatchAttemptResult(
                            maxCopies, false, false);
                });

        assertEquals(0L, remaining);
        assertEquals(List.of(17L, 17L, 17L, 17L, 16L, 16L), shares);
    }

    @Test
    void normalBatchRedistributesRejectedShareWithoutHistory() {
        var dispatch = new ProviderNormalDispatch();
        var targets = List.of(target(0), target(1), target(2));
        var shares = new ArrayList<Long>();

        long remaining = dispatch.dispatchBatch(
                targets,
                9L,
                (target, maxCopies) -> {
                    shares.add(maxCopies);
                    return new ProviderNormalDispatch.BatchAttemptResult(
                            target.equals(targets.getFirst()) ? 0L : maxCopies,
                            false,
                            false);
                });

        assertEquals(0L, remaining);
        assertEquals(List.of(3L, 5L, 4L), shares);
    }

    @Test
    void wirelessEvenModeRotatesPastAliveHardFailure() {
        var dispatch = new ProviderWirelessDispatch();
        var first = connection(0);
        var second = connection(1);
        dispatch.prepare(
                List.of(first, second),
                200L,
                false,
                WirelessDispatchMode.EVEN_DISTRIBUTION);
        var visited = new ArrayList<WirelessConnection>();

        boolean accepted = dispatch.dispatchSingleCopy(
                WirelessDispatchMode.EVEN_DISTRIBUTION,
                null,
                200L,
                false,
                2,
                target -> {
                    visited.add(target);
                    return target.equals(first)
                            ? WirelessPushOutcome.HARD_FAIL
                            : WirelessPushOutcome.SUCCESS;
                },
                target -> true,
                target -> {
                    throw new AssertionError("Alive target must not be removed");
                });

        assertTrue(accepted);
        assertEquals(List.of(first, second), visited);
    }

    @Test
    void wirelessDeadTargetReappearingInSameTopologyIsRequeued() {
        var dispatch = new ProviderWirelessDispatch();
        var connection = connection(0);
        var unchangedTopology = List.of(connection);
        dispatch.prepare(
                unchangedTopology,
                200L,
                false,
                WirelessDispatchMode.EVEN_DISTRIBUTION);

        boolean acceptedWhileOffline = dispatch.dispatchSingleCopy(
                WirelessDispatchMode.EVEN_DISTRIBUTION,
                null,
                200L,
                false,
                1,
                ignored -> WirelessPushOutcome.HARD_FAIL,
                ignored -> false,
                ignored -> {
                });

        assertFalse(acceptedWhileOffline);
        assertNull(dispatch.existingState(connection));

        // Validation can observe the target alive again before ever publishing
        // an empty topology. The same list must still rebuild the removed state.
        dispatch.prepare(
                unchangedTopology,
                201L,
                false,
                WirelessDispatchMode.EVEN_DISTRIBUTION);
        boolean acceptedAfterReload = dispatch.dispatchSingleCopy(
                WirelessDispatchMode.EVEN_DISTRIBUTION,
                null,
                201L,
                false,
                1,
                ignored -> WirelessPushOutcome.SUCCESS,
                ignored -> true,
                ignored -> {
                });

        assertTrue(acceptedAfterReload);
    }

    @Test
    void wirelessSingleModeSoftFailureLeavesTargetCoolingDown() {
        var dispatch = new ProviderWirelessDispatch();
        var connection = connection(0);
        dispatch.prepare(
                List.of(connection),
                300L,
                false,
                WirelessDispatchMode.SINGLE_TARGET);

        boolean accepted = dispatch.dispatchSingleCopy(
                WirelessDispatchMode.SINGLE_TARGET,
                null,
                300L,
                false,
                2,
                target -> WirelessPushOutcome.SOFT_FAIL,
                target -> true,
                target -> {
                });

        assertFalse(accepted);
        assertFalse(dispatch.existingState(connection).ready);
        assertTrue(dispatch.existingState(connection).cooldownUntil > 300L);
    }

    @Test
    void wirelessColdSmallBatchSpreadsAcrossDifferentTargets() {
        var dispatch = new ProviderWirelessDispatch();
        var pattern = new ExplosiveEqualityPattern();
        var connections = java.util.stream.IntStream.range(0, 8)
                .mapToObj(ProviderDispatchTest::connection)
                .toList();
        dispatch.prepare(
                connections,
                0L,
                false,
                WirelessDispatchMode.EVEN_DISTRIBUTION);
        var visited = new ArrayList<WirelessConnection>();

        long remaining = dispatch.dispatchBatch(
                WirelessDispatchMode.EVEN_DISTRIBUTION,
                pattern,
                3L,
                0L,
                false,
                (connection, allowance, exploratoryAttempt,
                        preserveBatchHistoryOnRejection) -> {
                    visited.add(connection);
                    return successfulBatch(1);
                },
                ignored -> true,
                ignored -> {
                    throw new AssertionError("live target must not be removed");
                });

        assertEquals(0L, remaining);
        assertEquals(3, visited.size());
        assertEquals(3, new java.util.HashSet<>(visited).size());
    }

    @Test
    void wirelessRejectedTargetsFailOnlyOnceAndDoNotConsumeCopies() {
        var dispatch = new ProviderWirelessDispatch();
        var pattern = new ExplosiveEqualityPattern();
        var connections = java.util.stream.IntStream.range(0, 8)
                .mapToObj(ProviderDispatchTest::connection)
                .toList();
        var rejected = new java.util.HashSet<>(connections.subList(0, 3));
        var attempts = new java.util.HashMap<WirelessConnection, Integer>();
        dispatch.prepare(
                connections,
                0L,
                false,
                WirelessDispatchMode.EVEN_DISTRIBUTION);

        long remaining = dispatch.dispatchBatch(
                WirelessDispatchMode.EVEN_DISTRIBUTION,
                pattern,
                5L,
                0L,
                false,
                (connection, allowance, exploratoryAttempt,
                        preserveBatchHistoryOnRejection) -> {
                    attempts.merge(connection, 1, Integer::sum);
                    return rejected.contains(connection)
                            ? rejectedBatch(1)
                            : successfulBatch(1);
                },
                ignored -> true,
                ignored -> {
                    throw new AssertionError("live target must not be removed");
                });

        assertEquals(0L, remaining);
        assertEquals(8, attempts.size());
        assertTrue(attempts.values().stream().allMatch(count -> count == 1));
        for (var connection : rejected) {
            assertEquals(
                    Long.MIN_VALUE,
                    dispatch.retryAfter(connection, pattern, 0L));
        }
    }

    @Test
    void wirelessLargeBatchSpreadsRefillsWithoutLosingSteadyThroughput() {
        int machineCount = 512;
        int measuredTicks = 100;
        var result = simulateWirelessBatch(
                machineCount,
                250,
                measuredTicks,
                ignored -> new SimulatedBatchMachine(64L, 1L));

        long theoreticalProcessed = (long) machineCount * measuredTicks;
        long fullScanVisits = (long) machineCount * measuredTicks;
        assertTrue(result.processed >= theoreticalProcessed * 95L / 100L,
                "steady throughput regressed: "
                        + result.processed + "/" + theoreticalProcessed
                        + ", visits=" + result.visits
                        + ", idleTicks=" + result.idleTicks);
        assertTrue(result.idleTicks <= machineCount * 5L,
                "too many avoidable empty-machine ticks: "
                        + result.idleTicks);
        assertTrue(result.visits <= fullScanVisits / 5L,
                "wireless refill still resembles an all-target scan: "
                        + result.visits + "/" + fullScanVisits);
        assertTrue(result.failures <= fullScanVisits / 16L,
                "rejected visits consumed too much of the full-scan budget: "
                        + result.failures + "/" + fullScanVisits);
        long minimumAccepted = result.acceptedByMachine.stream()
                .mapToLong(Long::longValue).min().orElseThrow();
        long maximumAccepted = result.acceptedByMachine.stream()
                .mapToLong(Long::longValue).max().orElseThrow();
        assertTrue(maximumAccepted <= 2L * Math.max(1L, minimumAccepted),
                "100-tick copy fairness exceeded 2:1: "
                        + minimumAccepted + ".." + maximumAccepted);
    }

    @Test
    void wirelessLargeInputBuffersDoNotMasqueradeAsPerTickThroughput() {
        int machineCount = 512;
        int measuredTicks = 100;
        var result = simulateWirelessBatch(
                machineCount,
                20,
                measuredTicks,
                ignored -> new SimulatedBatchMachine(Long.MAX_VALUE / 4L, 1L));

        long theoreticalProcessed = (long) machineCount * measuredTicks;
        long fullScanVisits = (long) machineCount * measuredTicks;
        assertTrue(result.processed >= theoreticalProcessed * 95L / 100L,
                "large-buffer cadence starved processing: "
                        + result.processed + "/" + theoreticalProcessed
                        + ", visits=" + result.visits
                        + ", idleTicks=" + result.idleTicks);
        assertTrue(result.visits <= fullScanVisits / 4L,
                "large input capacity was mistaken for per-tick throughput: "
                        + result.visits + "/" + fullScanVisits);
        assertEquals(0L, result.failures,
                "an effectively unbounded input buffer should not reject refills");
    }

    @Test
    void wirelessDifferentProcessingPeriodsKeepFastAndSlowTargetsBusy() {
        int machineCount = 128;
        int fastMachines = machineCount / 2;
        int measuredTicks = 200;
        var result = simulateWirelessBatch(
                machineCount,
                300,
                measuredTicks,
                index -> new SimulatedBatchMachine(
                        64L,
                        1L,
                        index < fastMachines ? 1 : 5));

        long theoreticalProcessed = (long) fastMachines * measuredTicks
                + (long) (machineCount - fastMachines) * (measuredTicks / 5L);
        long fullScanVisits = (long) machineCount * measuredTicks;
        long fastProcessed = result.processedByMachine.subList(0, fastMachines)
                .stream().mapToLong(Long::longValue).sum();
        long slowProcessed = result.processedByMachine.subList(
                        fastMachines, machineCount)
                .stream().mapToLong(Long::longValue).sum();
        long fastAccepted = result.acceptedByMachine.subList(0, fastMachines)
                .stream().mapToLong(Long::longValue).sum();
        long slowAccepted = result.acceptedByMachine.subList(
                        fastMachines, machineCount)
                .stream().mapToLong(Long::longValue).sum();

        assertTrue(result.processed >= theoreticalProcessed * 95L / 100L,
                "mixed-rate throughput regressed: "
                        + result.processed + "/" + theoreticalProcessed
                        + ", visits=" + result.visits
                        + ", idleTicks=" + result.idleTicks);
        assertTrue(fastProcessed >= slowProcessed * 4L,
                "fast targets were throttled toward slow-target fairness: "
                        + fastProcessed + "/" + slowProcessed);
        assertTrue(fastAccepted >= slowAccepted * 4L,
                "copy accounting throttled fast targets toward slow targets: "
                        + fastAccepted + "/" + slowAccepted);
        assertTrue(result.visits <= fullScanVisits / 5L,
                "mixed-rate scheduling still scans too many targets: "
                        + result.visits);
        assertTrue(result.failures <= fullScanVisits / 16L,
                "mixed-rate rejected visits consumed too much scan budget: "
                        + result.failures + "/" + fullScanVisits);
    }

    @Test
    void wirelessDispatchBookkeepingDoesNotInvokePatternEquality() {
        var pattern = new ExplosiveEqualityPattern();
        var wireless = new ProviderWirelessDispatch();
        var connection = connection(4);
        assertEquals(25L, wireless.recordRejection(
                connection, pattern, 20L, false));
        assertEquals(25L, wireless.retryAfter(
                connection, pattern, 20L));
        wireless.recordSuccess(connection, pattern, 20L, true);
        assertEquals(Long.MIN_VALUE, wireless.retryAfter(
                connection, pattern, 20L));
    }

    private static ProviderTarget target(int x) {
        return new ProviderTarget(
                Level.OVERWORLD,
                new BlockPos(x, 64, 0),
                Direction.NORTH);
    }

    private static WirelessConnection connection(int x) {
        return new WirelessConnection(
                Level.OVERWORLD,
                new BlockPos(x, 64, 0),
                Direction.NORTH);
    }

    private static ProviderWirelessDispatch.BatchAttemptResult successfulBatch(
            int copies) {
        return new ProviderWirelessDispatch.BatchAttemptResult(
                copies,
                copies,
                true,
                false,
                ProviderTarget.BaselineStatus.NONE,
                WirelessPushOutcome.SUCCESS);
    }

    private static ProviderWirelessDispatch.BatchAttemptResult rejectedBatch(
            int copies) {
        return new ProviderWirelessDispatch.BatchAttemptResult(
                0L,
                copies,
                false,
                false,
                ProviderTarget.BaselineStatus.NONE,
                WirelessPushOutcome.SOFT_FAIL);
    }

    private static SimulationResult simulateWirelessBatch(
            int machineCount,
            int warmupTicks,
            int measuredTicks,
            java.util.function.IntFunction<SimulatedBatchMachine> machineFactory) {
        var dispatch = new ProviderWirelessDispatch();
        var pattern = new ExplosiveEqualityPattern();
        var connections = java.util.stream.IntStream.range(0, machineCount)
                .mapToObj(ProviderDispatchTest::connection)
                .toList();
        var machines = new java.util.HashMap<WirelessConnection, SimulatedBatchMachine>();
        for (int index = 0; index < machineCount; index++) {
            machines.put(connections.get(index), machineFactory.apply(index));
        }

        long measuredVisits = 0L;
        long measuredProcessed = 0L;
        long measuredIdleTicks = 0L;
        long measuredFailures = 0L;
        int maxVisitsPerTick = 0;
        for (long tick = 0L; tick < warmupTicks + measuredTicks; tick++) {
            boolean measuring = tick >= warmupTicks;
            for (var machine : machines.values()) {
                long processed = machine.processTick(measuring);
                if (measuring) {
                    measuredProcessed += processed;
                    if (processed < machine.expectedThisTick) {
                        measuredIdleTicks++;
                    }
                }
            }

            dispatch.prepare(
                    connections,
                    tick,
                    false,
                    WirelessDispatchMode.EVEN_DISTRIBUTION);
            var visits = new AtomicInteger();
            var failures = new AtomicInteger();
            long currentTick = tick;
            dispatch.dispatchBatch(
                    WirelessDispatchMode.EVEN_DISTRIBUTION,
                    pattern,
                    1_000_000_000L,
                    tick,
                    false,
                    (connection, allowance, exploratoryAttempt,
                            preserveBatchHistoryOnRejection) -> {
                        visits.incrementAndGet();
                        var step = connection.pushPatternStep(
                                pattern,
                                allowance,
                                currentTick,
                                true,
                                preserveBatchHistoryOnRejection,
                                () -> false,
                                machines.get(connection)::pushChunk);
                        if (step.ownedCopies() <= 0L) {
                            failures.incrementAndGet();
                        }
                        return new ProviderWirelessDispatch.BatchAttemptResult(
                                step.ownedCopies(),
                                step.attemptedCopies(),
                                step.acceptedFullChunk(),
                                step.requestLimited(),
                                step.baselineStatus(),
                                step.globalAbort()
                                        ? WirelessPushOutcome.GLOBAL_ABORT
                                        : step.ownedCopies() > 0L
                                                ? WirelessPushOutcome.SUCCESS
                                                : WirelessPushOutcome.SOFT_FAIL);
                    },
                    ignored -> true,
                    ignored -> {
                        throw new AssertionError("live target must not be removed");
                    });
            if (measuring) {
                measuredVisits += visits.get();
                measuredFailures += failures.get();
                maxVisitsPerTick = Math.max(maxVisitsPerTick, visits.get());
            }
        }

        var processedByMachine = connections.stream()
                .map(connection -> machines.get(connection).measuredProcessed)
                .toList();
        var acceptedByMachine = connections.stream()
                .map(connection -> machines.get(connection).measuredAccepted)
                .toList();
        return new SimulationResult(
                measuredVisits,
                measuredProcessed,
                measuredIdleTicks,
                measuredFailures,
                maxVisitsPerTick,
                processedByMachine,
                acceptedByMachine);
    }

    private record SimulationResult(
            long visits,
            long processed,
            long idleTicks,
            long failures,
            int maxVisitsPerTick,
            List<Long> processedByMachine,
            List<Long> acceptedByMachine) {
    }

    private static final class SimulatedBatchMachine {
        private final long capacity;
        private final long rate;
        private final int processingInterval;
        private long stored;
        private long measuredProcessed;
        private long measuredAccepted;
        private long expectedThisTick;
        private long ticks;
        private boolean measuring;

        private SimulatedBatchMachine(long capacity, long rate) {
            this(capacity, rate, 1);
        }

        private SimulatedBatchMachine(
                long capacity,
                long rate,
                int processingInterval) {
            this.capacity = capacity;
            this.rate = rate;
            this.processingInterval = processingInterval;
        }

        private long processTick(boolean measuring) {
            this.measuring = measuring;
            expectedThisTick = ticks++ % processingInterval == 0L
                    ? rate
                    : 0L;
            long processed = Math.min(expectedThisTick, stored);
            stored -= processed;
            if (measuring) {
                measuredProcessed += processed;
            }
            return processed;
        }

        private ProviderTarget.BatchChunk pushChunk(int copies) {
            if (copies <= 0 || copies > capacity - stored) {
                return ProviderTarget.BatchChunk.REJECTED;
            }

            stored += copies;
            if (measuring) {
                measuredAccepted += copies;
            }
            return new ProviderTarget.BatchChunk(copies, true, false);
        }
    }

    private static final class ExplosiveEqualityPattern
            implements IPatternDetails {
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
            throw new AssertionError("third-party pattern hashCode must not run during dispatch");
        }
    }
}
