package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.SplittableRandom;
import java.util.TreeMap;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessDispatchMode;

/** Executable form of docs/adaptive-batch-dispatch-stress-model.md. */
@Tag("adaptive-batch-stress")
class AdaptiveBatchDispatchStressTest {
    private static final int TARGETS = 512;
    private static final int DIAGNOSTIC_WINDOW_TICKS = 20;
    private static final int ACCEPTANCE_WINDOW_TICKS = 100;
    private static final long RANDOM_SEED = 20260801L;
    private static final long PENDING_COPIES = Long.MAX_VALUE / 4L;

    private static final List<Model> MODELS = List.of(
            Model.fixed("A", 512, 1, 512),
            Model.fixed("B", 2048, 1, 512),
            Model.fixed("C", 512, 5, 512),
            Model.fixed("D", 2048, 5, 512),
            Model.fillFallback("E", 576, 20, 9),
            Model.random("R", 2048));

    @Test
    void convergesAndStaysWithinLimitsFor200Ticks() {
        assertModels(200);
    }

    @Test
    void doesNotRegressAcross5000Ticks() {
        assertModels(5_000);
    }

    @Test
    void finiteJobsRemainEvenAcrossAllTargets() {
        assertFiniteDistribution(512L, 1);
        assertFiniteDistribution(2_048L, 4);
    }

    @Test
    void relearnsWithinTenProcessingTimesAfterDispatchStateIsCleared() {
        var failures = new ArrayList<String>();
        var summaries = new ArrayList<String>();
        for (var model : MODELS) {
            // Leave one complete accepted window before the reset, and place
            // fixed models on a processing tick so the cold scheduler sees
            // newly available capacity immediately.
            int resetTick = model.startupTicks * 2
                    + ACCEPTANCE_WINDOW_TICKS;
            int recoveryTicks = model.startupTicks;
            int windowStart = resetTick + recoveryTicks;
            var result = simulate(
                    model,
                    windowStart + ACCEPTANCE_WINDOW_TICKS,
                    resetTick);

            long theoretical = sum(
                    result.theoreticalAt,
                    windowStart,
                    windowStart + ACCEPTANCE_WINDOW_TICKS);
            long processed = sum(
                    result.processedAt,
                    windowStart,
                    windowStart + ACCEPTANCE_WINDOW_TICKS);
            long pushes = sum(
                    result.physicalPushesAt,
                    windowStart,
                    windowStart + ACCEPTANCE_WINDOW_TICKS);
            double throughput = ratio(processed, theoretical);
            double dispatch = dispatchMetric(
                    model, pushes, theoretical, TARGETS);
            summaries.add(model.id + " reset@" + resetTick
                    + " recovery=" + recoveryTicks
                    + " throughput100=" + percent(throughput)
                    + " dispatch100=" + percent(dispatch));

            if (!atLeastPercent(
                    processed, theoretical, model.throughputPercent)) {
                failures.add(model.id + " did not recover within "
                        + recoveryTicks + " ticks: throughput="
                        + percent(throughput) + " < "
                        + model.throughputPercent + "%");
            }
            if (!atMostDispatchPercent(
                    model,
                    pushes,
                    theoretical,
                    TARGETS,
                    model.dispatchPercent)) {
                failures.add(model.id + " exceeded dispatch limit after "
                        + recoveryTicks + " recovery ticks: dispatch="
                        + percent(dispatch) + " > "
                        + model.dispatchPercent + "%");
            }
        }

        var report = "adaptive batch reset recovery\n"
                + String.join("\n", summaries);
        System.out.println(report);
        assertTrue(failures.isEmpty(), () -> report + "\n"
                + String.join("\n", failures));
    }

    private static void assertFiniteDistribution(
            long requestedCopies, int expectedPerTarget) {
        var dispatch = new ProviderWirelessDispatch();
        var pattern = new ExplosiveEqualityPattern();
        var model = Model.fillFallback("finite", 576, 20, 9);
        var connections = new ArrayList<WirelessConnection>(TARGETS);
        var machines = new HashMap<WirelessConnection, Machine>(TARGETS * 2);
        var accepted = new int[TARGETS];
        for (int target = 0; target < TARGETS; target++) {
            var connection = new WirelessConnection(
                    Level.OVERWORLD,
                    new BlockPos(target, 64, 0),
                    Direction.NORTH);
            connections.add(connection);
            machines.put(connection, new Machine(model, target, 16));
        }

        long remaining = requestedCopies;
        int physicalPushes = 0;
        for (int tick = 0; tick < 16 && remaining > 0L; tick++) {
            dispatch.prepare(
                    connections,
                    tick,
                    false,
                    WirelessDispatchMode.EVEN_DISTRIBUTION);
            int currentTick = tick;
            int[] pushesThisTick = {0};
            remaining = dispatch.dispatchBatch(
                    WirelessDispatchMode.EVEN_DISTRIBUTION,
                    pattern,
                    remaining,
                    tick,
                    false,
                    (connection, allowance, exploratoryAttempt,
                            preserveBatchHistoryOnRejection) -> {
                        int target = connection.pos().getX();
                        var step = connection.pushPatternStep(
                                pattern,
                                allowance,
                                currentTick,
                                true,
                                preserveBatchHistoryOnRejection,
                                () -> false,
                                copies -> {
                                    pushesThisTick[0]++;
                                    return machines.get(connection)
                                            .pushChunk(copies);
                                });
                        if (step.ownedCopies() > 0L) {
                            accepted[target] += (int) step.ownedCopies();
                        }
                        return batchAttempt(step);
                    },
                    ignored -> true,
                    ignored -> {
                        throw new AssertionError(
                                "live target must not be removed");
                    });
            physicalPushes += pushesThisTick[0];
        }

        assertEquals(0L, remaining,
                "finite request must finish during the safe cold ramp");
        for (int target = 0; target < TARGETS; target++) {
            assertEquals(expectedPerTarget, accepted[target],
                    "finite request skewed target " + target);
        }
        assertEquals(
                expectedPerTarget == 1 ? TARGETS : TARGETS * 3,
                physicalPushes,
                "safe 1,1,2 ramp must not revisit only a target subset");
    }

    private static void assertModels(int ticks) {
        var failures = new ArrayList<String>();
        var summaries = new ArrayList<String>();
        for (var model : MODELS) {
            int modelTicks = Math.max(
                    ticks,
                    model.startupTicks + ACCEPTANCE_WINDOW_TICKS);
            var result = simulate(model, modelTicks);
            var evaluation = evaluate(model, result);
            summaries.add(evaluation.summary());
            failures.addAll(evaluation.failures());
        }

        var report = "adaptive batch stress, ticks=" + ticks + '\n'
                + String.join("\n", summaries);
        System.out.println(report);
        assertTrue(failures.isEmpty(), () -> report + "\n"
                + String.join("\n", failures));
    }

    private static SimulationResult simulate(Model model, int ticks) {
        return simulate(model, ticks, -1);
    }

    private static SimulationResult simulate(
            Model model, int ticks, int resetTick) {
        var dispatch = new ProviderWirelessDispatch();
        var pattern = new ExplosiveEqualityPattern();
        var connections = new ArrayList<WirelessConnection>(TARGETS);
        var machines = new HashMap<WirelessConnection, Machine>(TARGETS * 2);
        for (int target = 0; target < TARGETS; target++) {
            var connection = new WirelessConnection(
                    Level.OVERWORLD,
                    new BlockPos(target, 64, 0),
                    Direction.NORTH);
            connections.add(connection);
            machines.put(connection, new Machine(model, target, ticks));
        }

        var result = new SimulationResult(model, ticks);
        for (int tick = 0; tick < ticks; tick++) {
            for (int target = 0; target < TARGETS; target++) {
                var machine = machines.get(connections.get(target));
                var processing = machine.process(tick);
                result.theoreticalAt[tick] += processing.theoretical();
                result.processedAt[tick] += processing.processed();
                result.targetTheoretical[target][tick] = processing.theoretical();
                result.targetProcessed[target][tick] = processing.processed();
                if (processing.theoretical() > 0) {
                    result.processingEvents++;
                    result.processingEventsAt[tick]++;
                    if (processing.processed() < processing.theoretical()) {
                        result.underfilledRuns++;
                    }
                }
                result.scheduleHash = 31L * result.scheduleHash
                        + processing.theoretical();
            }

            if (tick == resetTick) {
                // Models a runtime reload/rebuild that loses every adaptive
                // dispatch hint while preserving the machines' real contents.
                dispatch.patternsChanged();
            }

            dispatch.prepare(
                    connections,
                    tick,
                    false,
                    WirelessDispatchMode.EVEN_DISTRIBUTION);
            int currentTick = tick;
            dispatch.dispatchBatch(
                    WirelessDispatchMode.EVEN_DISTRIBUTION,
                    pattern,
                    PENDING_COPIES,
                    tick,
                    false,
                    (connection, allowance, exploratoryAttempt,
                            preserveBatchHistoryOnRejection) -> {
                        var machine = machines.get(connection);
                        int target = connection.pos().getX();
                        result.targetVisitsAt[currentTick]++;
                        var step = connection.pushPatternStep(
                                pattern,
                                allowance,
                                currentTick,
                                true,
                                preserveBatchHistoryOnRejection,
                                () -> false,
                                copies -> {
                                    result.physicalPushesAt[currentTick]++;
                                    result.targetPhysicalPushes[target]
                                            [currentTick]++;
                                    result.targetAttemptedCopies[target][currentTick]
                                            += copies;
                                    if (exploratoryAttempt) {
                                        result.targetExploratoryPushes[target]
                                                [currentTick]++;
                                    }
                                    var chunk = machine.pushChunk(copies);
                                    if (chunk.ownedCopies() <= 0L) {
                                        result.targetRejectedCopies[target]
                                                [currentTick] += copies;
                                        if (exploratoryAttempt) {
                                            result.targetRejectedExploratoryPushes
                                                    [target][currentTick]++;
                                        }
                                    }
                                    return chunk;
                                });
                        if (step.ownedCopies() > 0L) {
                            result.acceptedAt[currentTick] += step.ownedCopies();
                            result.targetAccepted[target][currentTick] +=
                                    (int) step.ownedCopies();
                        } else if (!step.globalAbort()) {
                            result.failedVisitsAt[currentTick]++;
                        }
                        return batchAttempt(step);
                    },
                    ignored -> true,
                    ignored -> {
                        throw new AssertionError("live target must not be removed");
                    });
        }

        long stored = 0L;
        for (var machine : machines.values()) {
            stored += machine.stored;
        }
        result.finalStored = stored;
        return result;
    }

    private static ProviderWirelessDispatch.BatchAttemptResult batchAttempt(
            ProviderTarget.BatchStepResult step) {
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
    }

    private static Evaluation evaluate(Model model, SimulationResult result) {
        var failures = new ArrayList<String>();
        double minimumTickThroughput = Double.POSITIVE_INFINITY;
        double minimumDiagnosticWindowThroughput = Double.POSITIVE_INFINITY;
        double minimumAcceptanceWindowThroughput = Double.POSITIVE_INFINITY;
        double maximumWindowDispatch = 0.0;
        int minimumAcceptanceWindowStart = -1;
        long maxVisitsPerTick = 0L;
        long totalTheoretical = 0L;
        long totalProcessed = 0L;
        long totalPushes = 0L;
        long totalFailures = 0L;
        long fullScanTicks = 0L;

        for (int tick = 0; tick < result.ticks; tick++) {
            totalTheoretical += result.theoreticalAt[tick];
            totalProcessed += result.processedAt[tick];
            totalPushes += result.physicalPushesAt[tick];
            totalFailures += result.failedVisitsAt[tick];
            if (result.targetVisitsAt[tick] == TARGETS) {
                fullScanTicks++;
            }
            maxVisitsPerTick = Math.max(
                    maxVisitsPerTick, result.physicalPushesAt[tick]);
            if (tick >= model.startupTicks
                    && result.theoreticalAt[tick] > 0L) {
                double ratio = ratio(
                        result.processedAt[tick], result.theoreticalAt[tick]);
                minimumTickThroughput = Math.min(minimumTickThroughput, ratio);
            }
        }

        for (int start = model.startupTicks;
             start + DIAGNOSTIC_WINDOW_TICKS <= result.ticks;
             start++) {
            long theoretical = sum(
                    result.theoreticalAt,
                    start,
                    start + DIAGNOSTIC_WINDOW_TICKS);
            long processed = sum(
                    result.processedAt,
                    start,
                    start + DIAGNOSTIC_WINDOW_TICKS);
            minimumDiagnosticWindowThroughput = Math.min(
                    minimumDiagnosticWindowThroughput,
                    ratio(processed, theoretical));
        }

        for (int start = model.startupTicks;
             start + ACCEPTANCE_WINDOW_TICKS <= result.ticks;
             start++) {
            long theoretical = sum(
                    result.theoreticalAt,
                    start,
                    start + ACCEPTANCE_WINDOW_TICKS);
            long processed = sum(
                    result.processedAt,
                    start,
                    start + ACCEPTANCE_WINDOW_TICKS);
            long pushes = sum(
                    result.physicalPushesAt,
                    start,
                    start + ACCEPTANCE_WINDOW_TICKS);
            double throughput = ratio(processed, theoretical);
            double dispatchMetric = dispatchMetric(
                    model, pushes, theoretical, TARGETS);
            if (throughput < minimumAcceptanceWindowThroughput) {
                minimumAcceptanceWindowThroughput = throughput;
                minimumAcceptanceWindowStart = start;
            }
            maximumWindowDispatch = Math.max(
                    maximumWindowDispatch, dispatchMetric);

            if (!atLeastPercent(
                    processed, theoretical, model.throughputPercent)) {
                recordFailure(failures, model.id + " window [" + start + ","
                        + (start + ACCEPTANCE_WINDOW_TICKS) + ") throughput="
                        + percent(throughput) + " < "
                        + model.throughputPercent + "%");
            }
            if (!atMostDispatchPercent(
                    model,
                    pushes,
                    theoretical,
                    TARGETS,
                    model.dispatchPercent)) {
                recordFailure(failures, model.id + " window [" + start + ","
                        + (start + ACCEPTANCE_WINDOW_TICKS) + ") dispatch="
                        + percent(dispatchMetric) + " > "
                        + model.dispatchPercent + "%");
            }
        }

        var targetWindow = checkPerTargetWindows(model, result, failures);
        var fairnessWindow = checkFairness(model, result, failures);

        long totalAccepted = sum(result.acceptedAt, 0, result.ticks);
        if (totalAccepted != totalProcessed + result.finalStored) {
            recordFailure(failures, model.id + " ownership mismatch: accepted="
                    + totalAccepted + ", processed=" + totalProcessed
                    + ", stored=" + result.finalStored);
        }

        if (!Double.isFinite(minimumTickThroughput)) {
            minimumTickThroughput = 1.0;
        }
        if (!Double.isFinite(minimumDiagnosticWindowThroughput)) {
            minimumDiagnosticWindowThroughput = 1.0;
        }
        if (!Double.isFinite(minimumAcceptanceWindowThroughput)) {
            minimumAcceptanceWindowThroughput = 1.0;
        }
        String summary = model.id
                + " throughput(total/min100)="
                + percent(ratio(totalProcessed, totalTheoretical)) + "/"
                + percent(minimumAcceptanceWindowThroughput)
                + ", dispatch(max100)=" + percent(maximumWindowDispatch)
                + ", diagnostic(minTick/min20)="
                + percent(minimumTickThroughput) + "/"
                + percent(minimumDiagnosticWindowThroughput)
                + ", pushes=" + totalPushes
                + ", failed=" + totalFailures
                + ", peak=" + maxVisitsPerTick
                + ", idle=" + (totalTheoretical - totalProcessed)
                + ", underfilled=" + result.underfilledRuns
                + ", fullScanTicks=" + fullScanTicks
                + ", acceptedRange100="
                + fairnessWindow.minimumAccepted() + ".."
                + fairnessWindow.maximumAccepted() + "@["
                + fairnessWindow.start() + ","
                + (fairnessWindow.start() + ACCEPTANCE_WINDOW_TICKS) + ")"
                + ", schedule(events/hash/seed)="
                + result.processingEvents + "/"
                + Long.toUnsignedString(result.scheduleHash, 16) + "/"
                + (model.random ? Long.toString(RANDOM_SEED) : "fixed")
                + ", targetDispatch(max100)="
                + percent(targetWindow.dispatchMetric())
                + "@" + targetWindow.target() + "/["
                + targetWindow.start() + ","
                + (targetWindow.start() + ACCEPTANCE_WINDOW_TICKS) + ")"
                + describeWindow(model, result, minimumAcceptanceWindowStart);
        return new Evaluation(summary, failures);
    }

    private static String describeWindow(
            Model model, SimulationResult result, int start) {
        if (start < 0) {
            return "";
        }
        int end = start + ACCEPTANCE_WINDOW_TICKS;
        var attempts = new TreeMap<Integer, long[]>();
        long exploratory = 0L;
        long rejectedExploratory = 0L;
        for (int target = 0; target < TARGETS; target++) {
            for (int tick = start; tick < end; tick++) {
                int attempted = result.targetAttemptedCopies[target][tick];
                if (attempted > 0) {
                    var counts = attempts.computeIfAbsent(
                            attempted, ignored -> new long[2]);
                    counts[0]++;
                    if (result.targetRejectedCopies[target][tick] > 0) {
                        counts[1]++;
                    }
                }
                exploratory += result.targetExploratoryPushes[target][tick];
                rejectedExploratory +=
                        result.targetRejectedExploratoryPushes[target][tick];
            }
        }
        var attemptSummary = new ArrayList<String>(attempts.size());
        for (var entry : attempts.entrySet()) {
            attemptSummary.add(entry.getKey() + "=" + entry.getValue()[0]
                    + "/" + entry.getValue()[1]);
        }
        String schedule = "";
        if (model.random) {
            var events = new ArrayList<String>(ACCEPTANCE_WINDOW_TICKS);
            for (int tick = start; tick < end; tick++) {
                events.add(tick + "=" + result.processingEventsAt[tick]
                        + "/" + result.theoreticalAt[tick]);
            }
            schedule = ", schedule={" + String.join(",", events) + "}";
        }
        return ", worst100=[" + start + "," + end + ")"
                + ", attempts/rejected={"
                + String.join(",", attemptSummary) + "}"
                + ", exploratory=" + exploratory + "/"
                + rejectedExploratory
                + schedule;
    }

    private static TargetWindow checkPerTargetWindows(
            Model model,
            SimulationResult result,
            List<String> failures) {
        double maximumDispatchMetric = 0.0;
        int maximumTarget = -1;
        int maximumStart = -1;
        for (int target = 0; target < TARGETS; target++) {
            long theoretical = sum(
                    result.targetTheoretical[target],
                    model.startupTicks,
                    model.startupTicks + ACCEPTANCE_WINDOW_TICKS);
            long pushes = sum(
                    result.targetPhysicalPushes[target],
                    model.startupTicks,
                    model.startupTicks + ACCEPTANCE_WINDOW_TICKS);
            long accepted = sum(
                    result.targetAccepted[target],
                    model.startupTicks,
                    model.startupTicks + ACCEPTANCE_WINDOW_TICKS);
            long successfulPushes = countPositive(
                    result.targetAccepted[target],
                    model.startupTicks,
                    model.startupTicks + ACCEPTANCE_WINDOW_TICKS);
            long attemptedCopies = sum(
                    result.targetAttemptedCopies[target],
                    model.startupTicks,
                    model.startupTicks + ACCEPTANCE_WINDOW_TICKS);
            long rejectedCopies = sum(
                    result.targetRejectedCopies[target],
                    model.startupTicks,
                    model.startupTicks + ACCEPTANCE_WINDOW_TICKS);
            long exploratoryPushes = sum(
                    result.targetExploratoryPushes[target],
                    model.startupTicks,
                    model.startupTicks + ACCEPTANCE_WINDOW_TICKS);
            long rejectedExploratoryPushes = sum(
                    result.targetRejectedExploratoryPushes[target],
                    model.startupTicks,
                    model.startupTicks + ACCEPTANCE_WINDOW_TICKS);
            for (int start = model.startupTicks;
                 start + ACCEPTANCE_WINDOW_TICKS <= result.ticks;
                 start++) {
                double targetDispatchMetric = dispatchMetric(
                        model, pushes, theoretical, 1);
                if (targetDispatchMetric > maximumDispatchMetric) {
                    maximumDispatchMetric = targetDispatchMetric;
                    maximumTarget = target;
                    maximumStart = start;
                }
                if (!atMostDispatchPercent(
                        model,
                        pushes,
                        theoretical,
                        1,
                        model.dispatchPercent)) {
                    recordFailure(failures, model.id + " target " + target
                            + " window [" + start + ","
                            + (start + ACCEPTANCE_WINDOW_TICKS) + ") dispatch="
                            + percent(dispatchMetric(
                                    model, pushes, theoretical, 1))
                            + " > " + model.dispatchPercent + "% (pushes="
                            + pushes + ", successful=" + successfulPushes
                            + ", accepted=" + accepted + ", theoretical="
                            + theoretical + ", attemptedCopies="
                            + attemptedCopies + ", rejectedCopies="
                            + rejectedCopies + ", exploratory="
                            + exploratoryPushes + "/"
                            + rejectedExploratoryPushes + ")");
                }
                if (start + ACCEPTANCE_WINDOW_TICKS < result.ticks) {
                    theoretical += result.targetTheoretical[target]
                            [start + ACCEPTANCE_WINDOW_TICKS];
                    theoretical -= result.targetTheoretical[target][start];
                    pushes += result.targetPhysicalPushes[target]
                            [start + ACCEPTANCE_WINDOW_TICKS];
                    pushes -= result.targetPhysicalPushes[target][start];
                    accepted += result.targetAccepted[target]
                            [start + ACCEPTANCE_WINDOW_TICKS];
                    accepted -= result.targetAccepted[target][start];
                    successfulPushes += result.targetAccepted[target]
                            [start + ACCEPTANCE_WINDOW_TICKS] > 0 ? 1 : 0;
                    successfulPushes -= result.targetAccepted[target][start]
                            > 0 ? 1 : 0;
                    attemptedCopies += result.targetAttemptedCopies[target]
                            [start + ACCEPTANCE_WINDOW_TICKS];
                    attemptedCopies -= result.targetAttemptedCopies[target][start];
                    rejectedCopies += result.targetRejectedCopies[target]
                            [start + ACCEPTANCE_WINDOW_TICKS];
                    rejectedCopies -= result.targetRejectedCopies[target][start];
                    exploratoryPushes += result.targetExploratoryPushes[target]
                            [start + ACCEPTANCE_WINDOW_TICKS];
                    exploratoryPushes -= result.targetExploratoryPushes[target][start];
                    rejectedExploratoryPushes +=
                            result.targetRejectedExploratoryPushes[target]
                                    [start + ACCEPTANCE_WINDOW_TICKS];
                    rejectedExploratoryPushes -=
                            result.targetRejectedExploratoryPushes[target][start];
                }
            }
        }
        return new TargetWindow(
                maximumDispatchMetric, maximumTarget, maximumStart);
    }

    private static FairnessWindow checkFairness(
            Model model,
            SimulationResult result,
            List<String> failures) {
        if (result.ticks < model.startupTicks + 100) {
            return new FairnessWindow(0L, 0L, -1);
        }
        var acceptedWindow = new long[TARGETS];
        var pushesWindow = new long[TARGETS];
        var theoreticalWindow = new long[TARGETS];
        for (int target = 0; target < TARGETS; target++) {
            acceptedWindow[target] = sum(
                    result.targetAccepted[target],
                    model.startupTicks,
                    model.startupTicks + 100);
            pushesWindow[target] = sum(
                    result.targetPhysicalPushes[target],
                    model.startupTicks,
                    model.startupTicks + 100);
            theoreticalWindow[target] = sum(
                    result.targetTheoretical[target],
                    model.startupTicks,
                    model.startupTicks + 100);
        }
        double worstRatio = -1.0;
        long worstMinimumAccepted = 0L;
        long worstMaximumAccepted = 0L;
        int worstStart = -1;
        for (int start = model.startupTicks;
             start + 100 <= result.ticks;
             start++) {
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = 0.0;
            int minimumTarget = -1;
            int maximumTarget = -1;
            long minimumAccepted = 0L;
            long minimumTheoretical = 0L;
            long maximumAccepted = 0L;
            long maximumTheoretical = 0L;
            for (int target = 0; target < TARGETS; target++) {
                long accepted = acceptedWindow[target];
                long physicalPushes = pushesWindow[target];
                long fairnessAmount = model.fillFallback
                        ? physicalPushes
                        : accepted;
                if (fairnessAmount <= 0L) {
                    recordFailure(failures, model.id + " target " + target
                            + (model.fillFallback
                                    ? " received no fallback visit in ["
                                    : " accepted no copies in [")
                            + start + ","
                            + (start + 100) + ")");
                    continue;
                }
                double comparable = fairnessAmount;
                if (model.random) {
                    long theoretical = theoreticalWindow[target];
                    comparable = ratio(accepted, theoretical);
                    if (comparable < minimum) {
                        minimumTheoretical = theoretical;
                    }
                    if (comparable > maximum) {
                        maximumTheoretical = theoretical;
                    }
                }
                if (comparable < minimum) {
                    minimum = comparable;
                    minimumTarget = target;
                    minimumAccepted = accepted;
                }
                if (comparable > maximum) {
                    maximum = comparable;
                    maximumTarget = target;
                    maximumAccepted = accepted;
                }
            }
            if (Double.isFinite(minimum) && maximum > 2.0 * minimum) {
                recordFailure(failures, model.id + " fairness in [" + start + ","
                        + (start + 100) + ")=" + minimum + " (target "
                        + minimumTarget + ", " + minimumAccepted + "/"
                        + minimumTheoretical + ").." + maximum + " (target "
                        + maximumTarget + ", " + maximumAccepted + "/"
                        + maximumTheoretical + ")");
            }
            double fairnessRatio = Double.isFinite(minimum) && minimum > 0.0
                    ? maximum / minimum
                    : Double.POSITIVE_INFINITY;
            if (fairnessRatio > worstRatio) {
                worstRatio = fairnessRatio;
                worstMinimumAccepted = minimumAccepted;
                worstMaximumAccepted = maximumAccepted;
                worstStart = start;
            }
            int nextTick = start + 100;
            if (nextTick < result.ticks) {
                for (int target = 0; target < TARGETS; target++) {
                    acceptedWindow[target] +=
                            result.targetAccepted[target][nextTick]
                                    - result.targetAccepted[target][start];
                    pushesWindow[target] +=
                            result.targetPhysicalPushes[target][nextTick]
                                    - result.targetPhysicalPushes[target][start];
                    theoreticalWindow[target] +=
                            result.targetTheoretical[target][nextTick]
                                    - result.targetTheoretical[target][start];
                }
            }
        }
        return new FairnessWindow(
                worstMinimumAccepted, worstMaximumAccepted, worstStart);
    }

    private static long sum(long[] values, int start, int end) {
        long result = 0L;
        for (int index = start; index < end; index++) {
            result += values[index];
        }
        return result;
    }

    private static void recordFailure(
            List<String> failures, String failure) {
        if (failures.size() < 128) {
            failures.add(failure);
        } else if (failures.size() == 128) {
            failures.add("additional failures omitted");
        }
    }

    private static long sum(int[] values, int start, int end) {
        long result = 0L;
        for (int index = start; index < end; index++) {
            result += values[index];
        }
        return result;
    }

    private static long countPositive(int[] values, int start, int end) {
        long result = 0L;
        for (int index = start; index < end; index++) {
            if (values[index] > 0) {
                result++;
            }
        }
        return result;
    }

    private static boolean atLeastPercent(
            long actual, long theoretical, int percent) {
        return actual * 100L >= theoretical * (long) percent;
    }

    private static boolean atMostDispatchPercent(
            Model model,
            long pushes,
            long theoretical,
            int targetCount,
            int percent) {
        if (model.fillFallback) {
            return pushes * 100L <= targetCount * (long) percent;
        }
        return theoretical > 0L
                && pushes * model.capacity * 100L
                        <= theoretical * (long) percent;
    }

    private static double ratio(long actual, long theoretical) {
        return theoretical <= 0L ? 1.0 : (double) actual / theoretical;
    }

    private static double dispatchMetric(
            Model model,
            long pushes,
            long theoretical,
            int targetCount) {
        if (model.fillFallback) {
            return targetCount <= 0
                    ? 0.0
                    : (double) pushes / targetCount;
        }
        return theoretical <= 0L
                ? 0.0
                : (double) pushes * model.capacity / theoretical;
    }

    private static String percent(double ratio) {
        return String.format(java.util.Locale.ROOT, "%.2f%%", ratio * 100.0);
    }

    private record Model(
            String id,
            int capacity,
            int period,
            int processingLimit,
            boolean random,
            boolean fillFallback,
            int startupTicks,
            int throughputPercent,
            int dispatchPercent) {
        private static Model fixed(
                String id, int capacity, int period, int processingLimit) {
            return new Model(
                    id, capacity, period, processingLimit,
                    false, false, period * 10, 95, 400);
        }

        private static Model fillFallback(
                String id, int capacity, int period, int processingLimit) {
            // The baseline is one safety visit per target per 100 ticks;
            // the shared 400% ceiling therefore permits at most four visits.
            return new Model(
                    id, capacity, period, processingLimit,
                    false, true, period * 10, 95, 400);
        }

        private static Model random(String id, int capacity) {
            return new Model(
                    id, capacity, 0, 0,
                    true, false, 50, 80, 800);
        }
    }

    private record Processing(int theoretical, int processed) {
    }

    private record Evaluation(String summary, List<String> failures) {
    }

    private record TargetWindow(
            double dispatchMetric, int target, int start) {
    }

    private record FairnessWindow(
            long minimumAccepted, long maximumAccepted, int start) {
    }

    private static final class SimulationResult {
        private final int ticks;
        private final long[] theoreticalAt;
        private final long[] processedAt;
        private final long[] acceptedAt;
        private final long[] physicalPushesAt;
        private final long[] failedVisitsAt;
        private final long[] targetVisitsAt;
        private final int[] processingEventsAt;
        private final int[][] targetTheoretical;
        private final int[][] targetProcessed;
        private final int[][] targetAccepted;
        private final int[][] targetPhysicalPushes;
        private final int[][] targetAttemptedCopies;
        private final int[][] targetRejectedCopies;
        private final int[][] targetExploratoryPushes;
        private final int[][] targetRejectedExploratoryPushes;
        private long finalStored;
        private long processingEvents;
        private long underfilledRuns;
        private long scheduleHash = 1L;

        private SimulationResult(Model model, int ticks) {
            this.ticks = ticks;
            theoreticalAt = new long[ticks];
            processedAt = new long[ticks];
            acceptedAt = new long[ticks];
            physicalPushesAt = new long[ticks];
            failedVisitsAt = new long[ticks];
            targetVisitsAt = new long[ticks];
            processingEventsAt = new int[ticks];
            targetTheoretical = new int[TARGETS][ticks];
            targetProcessed = new int[TARGETS][ticks];
            targetAccepted = new int[TARGETS][ticks];
            targetPhysicalPushes = new int[TARGETS][ticks];
            targetAttemptedCopies = new int[TARGETS][ticks];
            targetRejectedCopies = new int[TARGETS][ticks];
            targetExploratoryPushes = new int[TARGETS][ticks];
            targetRejectedExploratoryPushes = new int[TARGETS][ticks];
        }
    }

    private static final class Machine {
        private final Model model;
        private final int[] scheduledProcessing;
        private long stored;

        private Machine(Model model, int target, int ticks) {
            this.model = model;
            if (model.random) {
                scheduledProcessing = new int[ticks];
                var random = new SplittableRandom(RANDOM_SEED + target);
                int nextProcessingTick = random.nextInt(2, 6);
                int processingLimit = random.nextInt(512, 1025);
                while (nextProcessingTick < ticks) {
                    scheduledProcessing[nextProcessingTick] = processingLimit;
                    nextProcessingTick += random.nextInt(2, 6);
                    processingLimit = random.nextInt(512, 1025);
                }
            } else {
                scheduledProcessing = null;
            }
        }

        private Processing process(int tick) {
            int theoretical;
            if (model.random) {
                theoretical = scheduledProcessing[tick];
                if (theoretical == 0) {
                    return new Processing(0, 0);
                }
            } else {
                theoretical = tick % model.period == 0
                        ? model.processingLimit
                        : 0;
            }
            int processed = (int) Math.min(stored, theoretical);
            stored -= processed;
            return new Processing(theoretical, processed);
        }

        private ProviderTarget.BatchChunk pushChunk(int copies) {
            if (copies <= 0 || copies > model.capacity - stored) {
                return ProviderTarget.BatchChunk.REJECTED;
            }
            stored += copies;
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
            return 31;
        }
    }
}
