package com.moakiee.ae2lt.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;
import it.unimi.dsi.fastutil.objects.Reference2LongLinkedOpenHashMap;

import appeng.api.crafting.IPatternDetails;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessDispatchMode;

/** Complete runtime scheduler for wireless provider targets. */
final class ProviderWirelessDispatch {
    private static final int COOLDOWN_INIT = 5;
    private static final int COOLDOWN_MIN = 1;
    private static final int COOLDOWN_MAX = 40;
    private static final int COOLDOWN_NEAR_BAND = 4;
    private static final int COOLDOWN_STABLE_SUCCESSES = 2;
    private static final int REJECTION_HISTORY_TICKS = 100;
    private static final int IDLE_PATTERN_TICKS = 100;
    private static final int PATTERN_CLEANUP_BUDGET = 4;
    private static final float[] PROBE_LEVELS =
            {5f, 3f, 2f, 1f, 0.5f, 0.3f, 0.1f};

    private static final int WHEEL_BITS = 6;
    private static final int WHEEL_SIZE = 1 << WHEEL_BITS;
    private static final int WHEEL_MASK = WHEEL_SIZE - 1;

    private final WirelessOverflowQueue overflow = new WirelessOverflowQueue();
    private final Predicate<WirelessConnection> blocked = overflow::contains;
    private final Map<WirelessConnection, ConnectionState> states = new HashMap<>();
    private final List<WirelessConnection>[] wheel;
    private final ReadyQueue<WirelessConnection, ConnectionState> singleReady;
    private final ReadyQueue<WirelessConnection, ConnectionState> evenReady;
    private final DispatchFairnessScheduler<WirelessConnection, IPatternDetails> fairness =
            DispatchFairnessScheduler.forCanonicalPatterns();
    private final WirelessBatchCadence<WirelessConnection> batchCadence =
            new WirelessBatchCadence<>();
    private final Map<WirelessConnection, Map<IPatternDetails, Penalty>> penalties =
            new HashMap<>();
    private final DueTaskQueue<TargetPatternKey<WirelessConnection>> penaltyExpirations =
            new DueTaskQueue<>();
    // Access order finds cold patterns without scanning hot ones. One primitive
    // timestamp per pattern, never one expiry object per dispatch.
    private final Reference2LongLinkedOpenHashMap<IPatternDetails> patternActivity =
            new Reference2LongLinkedOpenHashMap<>();
    private final Runnable wakeMaintenance;
    private final Runnable saveHistoryChanges;
    private long lastMaintenanceTick = Long.MIN_VALUE;

    private List<WirelessConnection> validReference = List.of();
    private boolean structuresDirty = true;
    private long lastWheelTick = -1L;
    private long topologyVersion;

    ProviderWirelessDispatch() {
        this(() -> {}, () -> {});
    }

    @SuppressWarnings("unchecked")
    ProviderWirelessDispatch(Runnable wakeMaintenance, Runnable saveHistoryChanges) {
        this.wakeMaintenance = wakeMaintenance;
        this.saveHistoryChanges = saveHistoryChanges;
        wheel = new List[WHEEL_SIZE];
        for (int i = 0; i < wheel.length; i++) {
            wheel[i] = new ArrayList<>();
        }
        singleReady = new ReadyQueue<>(states::get);
        evenReady = new ReadyQueue<>(states::get);
    }

    WirelessOverflowQueue overflow() {
        return overflow;
    }

    ConnectionState state(WirelessConnection connection) {
        return states.computeIfAbsent(
                connection, ignored -> new ConnectionState());
    }

    @Nullable
    ConnectionState existingState(WirelessConnection connection) {
        return states.get(connection);
    }

    ReadyQueue<WirelessConnection, ConnectionState> readyQueue(
            WirelessDispatchMode mode) {
        return mode == WirelessDispatchMode.SINGLE_TARGET
                ? singleReady
                : evenReady;
    }

    DispatchFairnessScheduler<WirelessConnection, IPatternDetails>.Pass beginFairPass(
            IPatternDetails pattern, long gameTick) {
        touchPattern(pattern, gameTick);
        return fairness.beginPass(
                pattern, validReference, topologyVersion, gameTick);
    }

    long retryAfter(
            WirelessConnection target,
            IPatternDetails pattern,
            long gameTick) {
        purgeExpiredPenalties(gameTick);
        var byPattern = penalties.get(target);
        if (byPattern == null) {
            return Long.MIN_VALUE;
        }
        var penalty = byPattern.get(pattern);
        return penalty == null || penalty.retryAfter <= gameTick
                ? Long.MIN_VALUE : penalty.retryAfter;
    }

    long recordRejection(
            WirelessConnection target,
            IPatternDetails pattern,
            long gameTick,
            boolean fast) {
        purgeExpiredPenalties(gameTick);
        int maximum = fast ? 10 : 40;
        var byPattern = penalties.computeIfAbsent(
                target, ignored -> CanonicalPatternMaps.create());
        var penalty = byPattern.get(pattern);
        if (penalty == null) {
            penalty = new Penalty();
            byPattern.put(pattern, penalty);
            penaltyExpirations.schedule(new TargetPatternKey<>(target, pattern),
                    gameTick + REJECTION_HISTORY_TICKS);
        }
        penalty.lastActivityTick = gameTick;
        penalty.retryAfter = gameTick + penalty.schedule.failure(gameTick, maximum);
        return penalty.retryAfter;
    }

    void recordSuccess(
            WirelessConnection target, IPatternDetails pattern, long gameTick,
            boolean scheduleNextPoll) {
        var byPattern = penalties.get(target);
        var penalty = byPattern == null ? null : byPattern.get(pattern);
        if (penalty != null) {
            int delay = penalty.schedule.success(gameTick);
            penalty.lastActivityTick = gameTick;
            // Adaptive batches already have their own amount-aware cadence.
            penalty.retryAfter = scheduleNextPoll && delay > 1
                    ? gameTick + delay : Long.MIN_VALUE;
        }
    }

    private void removeRejectionHistory(WirelessConnection target, IPatternDetails pattern) {
        var byPattern = penalties.get(target);
        if (byPattern == null || byPattern.remove(pattern) == null) {
            return;
        }
        penaltyExpirations.remove(new TargetPatternKey<>(target, pattern));
        if (byPattern.isEmpty()) {
            penalties.remove(target);
        }
    }

    void pauseTarget(WirelessConnection target) {
        fairness.pauseTarget(target);
    }

    void excludeUntil(
            IPatternDetails pattern,
            WirelessConnection target,
            long retryAfter,
            long gameTick) {
        fairness.excludeUntil(pattern, target, retryAfter, gameTick);
    }

    void resumeTarget(WirelessConnection target, long gameTick) {
        fairness.resumeTarget(target, gameTick);
    }

    void removeTarget(WirelessConnection target) {
        removePenalties(target);
        batchCadence.removeTarget(target);
        fairness.removeTarget(target);
        evenReady.remove(target);
        singleReady.remove(target);
        if (states.remove(target) != null) {
            // A transient hard failure can remove a target while the validated
            // connection list still contains the same address. Force prepare()
            // to rebuild even when the next topology snapshot compares equal,
            // otherwise the recovered target is never added back to a ready queue.
            structuresDirty = true;
        }
    }

    void patternsChanged() {
        patternActivity.clear();
        penalties.clear();
        penaltyExpirations.clear();
        batchCadence.clear();
        fairness.clear();
        for (var connection : states.keySet()) {
            ((ProviderTarget) connection).clearBatchHistory();
        }
    }

    void prepare(
            List<WirelessConnection> valid,
            long gameTick,
            boolean fastMode,
            WirelessDispatchMode mode) {
        maintain(gameTick);
        if (structuresDirty || valid != validReference) {
            if (!structuresDirty && valid.equals(validReference)) {
                validReference = valid;
            } else {
                rebuild(valid, gameTick, mode);
            }
        }
        advance(gameTick, fastMode, mode);
    }

    long dispatchBatch(
            WirelessDispatchMode mode,
            IPatternDetails pattern,
            long maxCopies,
            long gameTick,
            boolean fastMode,
            BatchAttempt attempt,
            Predicate<WirelessConnection> alive,
            Consumer<WirelessConnection> targetRemoved) {
        return dispatchBatch(mode, pattern, maxCopies, gameTick, fastMode, 1,
                attempt, alive, targetRemoved);
    }

    long dispatchBatch(
            WirelessDispatchMode mode,
            IPatternDetails pattern,
            long maxCopies,
            long gameTick,
            boolean fastMode,
            int machineParallelism,
            BatchAttempt attempt,
            Predicate<WirelessConnection> alive,
            Consumer<WirelessConnection> targetRemoved) {
        if (mode == WirelessDispatchMode.SINGLE_TARGET) {
            touchPattern(pattern, gameTick);
            return dispatchSingleTargetBatch(
                    maxCopies, gameTick, fastMode,
                    attempt, alive, targetRemoved);
        }
        return dispatchFairBatch(
                pattern, maxCopies, gameTick, fastMode, machineParallelism,
                attempt, alive, targetRemoved);
    }

    boolean dispatchSingleCopy(
            WirelessDispatchMode mode,
            IPatternDetails pattern,
            long gameTick,
            boolean fastMode,
            int targetAttempts,
            SingleAttempt attempt,
            Predicate<WirelessConnection> alive,
            Consumer<WirelessConnection> targetRemoved) {
        if (mode == WirelessDispatchMode.EVEN_DISTRIBUTION) touchPattern(pattern, gameTick);
        var readyQueue = readyQueue(mode);
        int scanBudget = Math.min(targetAttempts, readyQueue.size());
        while (scanBudget-- > 0 && !readyQueue.isEmpty()) {
            var connection = readyQueue.peek();
            if (blocked.test(connection)) {
                readyQueue.removeHead();
                continue;
            }
            if (mode == WirelessDispatchMode.EVEN_DISTRIBUTION
                    && retryAfter(connection, pattern, gameTick) > gameTick) {
                readyQueue.rotateHeadToTail();
                continue;
            }

            var state = existingState(connection);
            if (state == null || !state.ready) {
                readyQueue.removeHead();
                continue;
            }
            boolean probing = isProbing(state, gameTick);
            var outcome = attempt.push(connection);
            if (outcome.consumesTargetAttempt()) {
                state.probeArmed = false;
            }

            switch (outcome) {
                case SUCCESS -> {
                    if (mode == WirelessDispatchMode.EVEN_DISTRIBUTION) {
                        recordSuccess(connection, pattern, gameTick, true);
                    }
                    recordPushSuccess(state, probing, gameTick);
                    if (blocked.test(connection)) {
                        if (mode == WirelessDispatchMode.EVEN_DISTRIBUTION) {
                            pauseTarget(connection);
                        }
                        readyQueue.removeHead();
                    } else if (mode == WirelessDispatchMode.EVEN_DISTRIBUTION) {
                        readyQueue.rotateHeadToTail();
                    }
                    return true;
                }
                case HARD_FAIL -> {
                    readyQueue.removeHead();
                    if (!alive.test(connection)) {
                        removeTarget(connection);
                        targetRemoved.accept(connection);
                    } else if (mode == WirelessDispatchMode.EVEN_DISTRIBUTION) {
                        readyQueue.offer(connection);
                    }
                }
                case SOFT_FAIL -> {
                    if (mode == WirelessDispatchMode.SINGLE_TARGET) {
                        readyQueue.removeHead();
                        recordPushFailure(state, probing, gameTick);
                        scheduleAfterFailure(
                                connection, state, fastMode, mode);
                    } else {
                        long due = recordRejection(
                                connection, pattern, gameTick, fastMode);
                        excludeUntil(pattern, connection, due, gameTick);
                        readyQueue.rotateHeadToTail();
                    }
                }
                case GLOBAL_ABORT -> {
                    return false;
                }
            }
        }
        return false;
    }

    private long dispatchSingleTargetBatch(
            long maxCopies,
            long gameTick,
            boolean fastMode,
            BatchAttempt attempt,
            Predicate<WirelessConnection> alive,
            Consumer<WirelessConnection> targetRemoved) {
        var readyQueue = singleReady;
        long remaining = maxCopies;
        int attemptBudget = Math.min(1, readyQueue.size());
        for (int attempts = 0;
             attempts < attemptBudget && remaining > 0L && !readyQueue.isEmpty();
             attempts++) {
            var connection = readyQueue.peek();
            if (blocked.test(connection)) {
                readyQueue.removeHead();
                continue;
            }
            var state = existingState(connection);
            if (state == null || !state.ready) {
                readyQueue.removeHead();
                continue;
            }
            boolean probing = isProbing(state, gameTick);
            var result = attempt.push(
                    connection, remaining, false, false);
            if (result.outcome.consumesTargetAttempt()) {
                state.probeArmed = false;
            }
            if (result.ownedCopies > 0L) {
                remaining -= result.ownedCopies;
                recordPushSuccess(state, probing, gameTick);
                if (blocked.test(connection)) {
                    readyQueue.removeHead();
                }
                return remaining;
            }

            switch (result.outcome) {
                case HARD_FAIL -> {
                    readyQueue.removeHead();
                    if (!alive.test(connection)) {
                        removeTarget(connection);
                        targetRemoved.accept(connection);
                    }
                }
                case SOFT_FAIL -> {
                    readyQueue.removeHead();
                    recordPushFailure(state, probing, gameTick);
                    scheduleAfterFailure(
                            connection, state, fastMode,
                            WirelessDispatchMode.SINGLE_TARGET);
                }
                case GLOBAL_ABORT -> {
                    return remaining;
                }
                case SUCCESS -> {
                }
            }
        }
        return remaining;
    }

    static int groupedTargetCount(long copies, int parallelism, int availableTargets) {
        if (copies <= 0L || availableTargets <= 0) {
            return 0;
        }
        return (int) Math.min(availableTargets,
                1L + (copies - 1L) / Math.max(1, parallelism));
    }

    private long dispatchFairBatch(
            IPatternDetails pattern,
            long maxCopies,
            long gameTick,
            boolean fastMode,
            int machineParallelism,
            BatchAttempt attempt,
            Predicate<WirelessConnection> alive,
            Consumer<WirelessConnection> targetRemoved) {
        long remaining = maxCopies;
        try (var pass = beginFairPass(pattern, gameTick)) {
            int attemptBudget = pass.activeTargetsAtStart();
            boolean grouped = machineParallelism > 1;
            int targetLimit = grouped
                    ? groupedTargetCount(maxCopies, machineParallelism, attemptBudget)
                    : attemptBudget;
            int successfulTargets = 0;
            for (int attempts = 0;
                 attempts < attemptBudget && remaining > 0L
                        && successfulTargets < targetLimit;
                 attempts++) {
                var connection = pass.poll();
                if (connection == null) {
                    break;
                }
                if (blocked.test(connection)) {
                    pauseTarget(connection);
                    continue;
                }

                long retryAfter = retryAfter(connection, pattern, gameTick);
                if (retryAfter > gameTick) {
                    pass.cooldown(connection, retryAfter);
                    continue;
                }
                var state = existingState(connection);
                if (state == null) {
                    pass.remove(connection);
                    continue;
                }
                if (!state.ready) {
                    pass.cooldown(
                            connection,
                            Math.max(gameTick + 1L, state.cooldownUntil));
                    continue;
                }

                boolean probing = isProbing(state, gameTick);
                boolean exploratoryAttempt =
                        batchCadence.isExploratoryAttempt(
                                connection, pattern);
                boolean reopenReservoirTail =
                        batchCadence.shouldReopenReservoirTail(
                                connection, pattern);
                if (reopenReservoirTail) {
                    ((ProviderTarget) connection)
                            .reopenReservoirTailAudit(pattern, gameTick);
                }
                // P is a grouping hint, not a physical batch size. Let the
                // existing H,H,2H ramp prove what the selected machine accepts.
                int groupedSlotsLeft = Math.min(
                        targetLimit - successfulTargets, attemptBudget - attempts);
                long groupedShare = grouped
                        ? 1L + (remaining - 1L) / groupedSlotsLeft
                        : remaining;
                long share = Math.min(
                        remaining, grouped
                                ? pass.raiseAllowance(connection, groupedShare)
                                : pass.allowance(connection));
                int targetsLeft = attemptBudget - attempts;
                long equalShareLimit = targetsLeft <= 0
                        ? 0L
                        : remaining / targetsLeft;
                long rampAllowance = ((ProviderTarget) connection)
                        .batchStepRampAllowance(
                                pattern, equalShareLimit, gameTick);
                if (rampAllowance > share
                        && rampAllowance <= equalShareLimit) {
                    share = Math.min(
                            remaining,
                            pass.raiseAllowance(
                                    connection, rampAllowance));
                }
                if (batchCadence.usesSingleChunkRefill(connection, pattern)
                        && (!exploratoryAttempt || !reopenReservoirTail)) {
                    int candidate = ((ProviderTarget) connection)
                            .batchStepProvenChunk(
                                    pattern, remaining, gameTick);
                    share = Math.min(share, candidate);
                }
                if (grouped) {
                    share = Math.min(share, groupedShare);
                }
                if (share <= 0L) {
                    continue;
                }
                boolean preserveBatchHistory =
                        batchCadence.shouldPreserveBatchHistory(
                                connection, pattern, gameTick)
                        || ((ProviderTarget) connection)
                                .hasReservoirBatchState(pattern, gameTick);
                var result = attempt.push(
                        connection,
                        share,
                        exploratoryAttempt,
                        preserveBatchHistory);
                if (result.outcome.consumesTargetAttempt()) {
                    state.probeArmed = false;
                }
                if (result.ownedCopies > 0L) {
                    // A partially accepting machine must not strand the remainder
                    // while backup machines are idle. Each target still gets at most one visit.
                    if (!grouped || result.ownedCopies >= groupedShare) {
                        successfulTargets++;
                    }
                    int coverageTicks = batchCadence.recordSuccess(
                            connection,
                            pattern,
                            gameTick,
                            result.ownedCopies,
                            result.acceptedFullChunk,
                            result.baselineStatus);
                    pass.successAndCover(
                            connection, result.ownedCopies, coverageTicks);
                    recordSuccess(connection, pattern, gameTick, false);
                    remaining -= result.ownedCopies;
                    recordPushSuccess(state, probing, gameTick);
                    if (blocked.test(connection)) {
                        pauseTarget(connection);
                        evenReady.remove(connection);
                    }
                    if (result.outcome == WirelessPushOutcome.GLOBAL_ABORT) {
                        return remaining;
                    }
                    continue;
                }

                switch (result.outcome) {
                    case HARD_FAIL -> {
                        int retryDelay = recordBatchFailure(
                                connection, pattern, gameTick, result);
                        if (!alive.test(connection)) {
                            pass.remove(connection);
                            removeTarget(connection);
                            targetRemoved.accept(connection);
                        } else {
                            long due = batchRetryAfter(
                                    connection, pattern, gameTick,
                                    fastMode, result, retryDelay);
                            pass.cooldown(connection, due);
                        }
                    }
                    case SOFT_FAIL -> {
                        int retryDelay = recordBatchFailure(
                                connection, pattern, gameTick, result);
                        long due = batchRetryAfter(
                                connection, pattern, gameTick,
                                fastMode, result, retryDelay);
                        pass.cooldown(connection, due);
                    }
                    case GLOBAL_ABORT -> {
                        return remaining;
                    }
                    case SUCCESS -> {
                    }
                }
            }
        }
        return remaining;
    }

    private int recordBatchFailure(
            WirelessConnection connection,
            IPatternDetails pattern,
            long gameTick,
            BatchAttemptResult result) {
        if (result.attemptedCopies > 0) {
            return batchCadence.recordFailure(connection, pattern, gameTick);
        }
        return 0;
    }

    private long batchRetryAfter(
            WirelessConnection connection,
            IPatternDetails pattern,
            long gameTick,
            boolean fastMode,
            BatchAttemptResult result,
            int retryDelay) {
        if (result.attemptedCopies > 0) {
            return gameTick + Math.max(1, retryDelay);
        }
        return recordRejection(connection, pattern, gameTick, fastMode);
    }

    private static boolean isProbing(
            ConnectionState state, long gameTick) {
        return state.probeArmed
                && state.cooldownUntil >= 0L
                && gameTick < state.cooldownUntil;
    }

    private static void recordPushSuccess(
            ConnectionState state, boolean probing, long gameTick) {
        if (probing) {
            state.onProbeSuccess();
        } else {
            state.onPushSuccess(gameTick);
        }
    }

    private static void recordPushFailure(
            ConnectionState state, boolean probing, long gameTick) {
        if (probing) {
            state.onProbeFail();
        } else {
            state.onPushFail(gameTick);
        }
    }

    void scheduleAfterFailure(
            WirelessConnection connection,
            ConnectionState state,
            boolean fastMode,
            WirelessDispatchMode mode) {
        if (blocked.test(connection)) {
            return;
        }
        state.ready = false;
        state.probeArmed = false;
        if (state.cooldownUntil < 0) {
            state.ready = true;
            enqueue(connection, state, mode);
            return;
        }

        long targetTick = state.cooldownUntil;
        if (fastMode && !state.probedThisCycle) {
            float level = PROBE_LEVELS[state.probeLevelIndex];
            if (level >= 1.0f) {
                long probeTick = state.cooldownUntil - (int) level;
                if (probeTick > lastWheelTick) {
                    targetTick = probeTick;
                }
            } else {
                int interval = Math.round(1.0f / level);
                if (state.probeSkipCounter >= interval) {
                    long probeTick = state.cooldownUntil - 1;
                    if (probeTick > lastWheelTick) {
                        targetTick = probeTick;
                    }
                }
            }
        }
        targetTick = Math.max(targetTick, lastWheelTick + 1);
        wheel[(int) (targetTick & WHEEL_MASK)].add(connection);
    }

    void markDirty() {
        structuresDirty = true;
    }

    void retainStates(Iterable<WirelessConnection> valid) {
        var retained = new java.util.HashSet<WirelessConnection>();
        for (var connection : valid) {
            retained.add(connection);
        }
        var removed = new ArrayList<WirelessConnection>();
        for (var connection : states.keySet()) {
            if (!retained.contains(connection)) {
                removed.add(connection);
            }
        }
        for (var connection : removed) {
            removeTarget(connection);
        }
    }

    void clear() {
        validReference = List.of();
        structuresDirty = true;
        lastWheelTick = -1L;
        for (var slot : wheel) {
            slot.clear();
        }
        clearReadyQueues();
        patternsChanged();
        states.clear();
    }

    private void removePenalties(WirelessConnection target) {
        var byPattern = penalties.remove(target);
        if (byPattern == null) {
            return;
        }
        for (var pattern : byPattern.keySet()) {
            penaltyExpirations.remove(
                    new TargetPatternKey<>(target, pattern));
        }
    }

    private void touchPattern(IPatternDetails pattern, long gameTick) {
        boolean wasEmpty = patternActivity.isEmpty();
        patternActivity.putAndMoveToLast(pattern, gameTick);
        if (wasEmpty) wakeMaintenance.run();
    }

    boolean hasMaintenanceWork() {
        return !patternActivity.isEmpty() || penaltyExpirations.size() > 0;
    }

    void registerRestoredHistory(List<WirelessConnection> connections, long gameTick) {
        for (var target : connections) {
            state(target);
            ((ProviderTarget) target).forEachBatchHistoryPattern(
                    pattern -> touchPattern(pattern, gameTick));
        }
    }

    void maintain(long gameTick) {
        if (lastMaintenanceTick == gameTick) return;
        lastMaintenanceTick = gameTick;
        purgeExpiredPenalties(gameTick);
        boolean historyRemoved = false;
        for (int budget = PATTERN_CLEANUP_BUDGET; budget > 0 && !patternActivity.isEmpty(); budget--) {
            var pattern = patternActivity.firstKey();
            long lastUsed = patternActivity.getLong(pattern);
            if (gameTick >= lastUsed && gameTick - lastUsed <= IDLE_PATTERN_TICKS) break;
            if (!fairness.removePattern(pattern)) {
                // A reentrant world callback must not invalidate an open pass.
                patternActivity.putAndMoveToLast(pattern, gameTick);
                continue;
            }
            patternActivity.removeFirstLong();
            historyRemoved = true;
            for (var target : states.keySet()) {
                batchCadence.removePattern(target, pattern);
                ((ProviderTarget) target).clearBatchHistory(pattern);
                removeRejectionHistory(target, pattern);
            }
        }
        if (historyRemoved) saveHistoryChanges.run();
    }

    private void purgeExpiredPenalties(long gameTick) {
        TargetPatternKey<WirelessConnection> expired;
        while ((expired = penaltyExpirations.pollDue(gameTick)) != null) {
            var byPattern = penalties.get(expired.target());
            if (byPattern == null) {
                continue;
            }
            var penalty = byPattern.get(expired.pattern());
            if (penalty == null) continue;
            // Renew only when the old expiry is reached, not on every transfer.
            // Retry readiness and forgetting the learned period are separate.
            long expires = penalty.lastActivityTick + REJECTION_HISTORY_TICKS;
            if (expires > gameTick) {
                penaltyExpirations.schedule(expired, expires);
                continue;
            }
            byPattern.remove(expired.pattern());
            if (byPattern.isEmpty()) {
                penalties.remove(expired.target());
            }
        }
    }

    private void rebuild(
            List<WirelessConnection> valid,
            long gameTick,
            WirelessDispatchMode mode) {
        for (var slot : wheel) {
            slot.clear();
        }
        clearReadyQueues();
        for (var connection : valid) {
            if (blocked.test(connection)) {
                continue;
            }
            var state = state(connection);
            if (state.cooldownUntil >= 0 && gameTick < state.cooldownUntil) {
                state.ready = false;
                state.probeArmed = false;
                wheel[(int) (state.cooldownUntil & WHEEL_MASK)].add(connection);
            } else {
                state.ready = true;
                state.probeArmed = false;
                enqueue(connection, state, mode);
            }
        }
        validReference = valid;
        topologyVersion++;
        structuresDirty = false;
        lastWheelTick = gameTick;
    }

    private void advance(
            long gameTick,
            boolean fastMode,
            WirelessDispatchMode mode) {
        if (lastWheelTick < 0) {
            lastWheelTick = gameTick - 1;
        }
        long delta = gameTick - lastWheelTick;
        if (delta <= 0) {
            return;
        }
        int steps = (int) Math.min(delta, WHEEL_SIZE);
        for (int i = 0; i < steps; i++) {
            long tick = lastWheelTick + 1 + i;
            var slot = wheel[(int) (tick & WHEEL_MASK)];
            var iterator = slot.iterator();
            while (iterator.hasNext()) {
                var connection = iterator.next();
                if (blocked.test(connection)) {
                    iterator.remove();
                    continue;
                }
                var state = states.get(connection);
                if (state == null) {
                    iterator.remove();
                    continue;
                }
                boolean fire = state.cooldownUntil < 0
                        || tick >= state.cooldownUntil;
                boolean probe = false;
                if (!fire && fastMode && !state.probedThisCycle
                        && state.isInProbeWindow(tick)) {
                    fire = true;
                    probe = true;
                }
                if (fire) {
                    iterator.remove();
                    state.ready = true;
                    state.probeArmed = probe;
                    enqueue(connection, state, mode);
                }
            }
        }
        lastWheelTick = gameTick;
    }

    private void enqueue(
            WirelessConnection connection,
            ConnectionState state,
            WirelessDispatchMode mode) {
        if (!state.ready || blocked.test(connection)) {
            return;
        }
        readyQueue(mode).offer(connection);
    }

    private void clearReadyQueues() {
        singleReady.clear();
        evenReady.clear();
        for (var state : states.values()) {
            state.setQueued(false);
        }
    }

    static final class ConnectionState implements ReadyQueue.State {
        long cooldownUntil = -1L;
        int cooldown = COOLDOWN_INIT;
        int searchLow = COOLDOWN_MIN;
        int searchHigh = COOLDOWN_MAX;
        int stableSuccesses;
        int failureStreak;
        int probeLevelIndex;
        int probeSkipCounter;
        boolean probedThisCycle;
        boolean ready = true;
        boolean probeArmed;
        boolean queued;

        boolean isInProbeWindow(long gameTick) {
            if (cooldownUntil < 0 || gameTick >= cooldownUntil
                    || probedThisCycle) {
                return false;
            }
            float level = PROBE_LEVELS[probeLevelIndex];
            if (level >= 1.0f) {
                return gameTick == cooldownUntil - (int) level;
            }
            int interval = Math.round(1.0f / level);
            return probeSkipCounter >= interval
                    && gameTick == cooldownUntil - 1;
        }

        void onProbeSuccess() {
            probeLevelIndex = 0;
            probeSkipCounter = 0;
            probedThisCycle = true;
            cooldownUntil = -1L;
        }

        void onProbeFail() {
            probeLevelIndex = Math.min(
                    probeLevelIndex + 1, PROBE_LEVELS.length - 1);
            probeSkipCounter = 0;
            probedThisCycle = true;
        }

        void onPushSuccess(long gameTick) {
            if (cooldownUntil < 0) {
                return;
            }
            failureStreak = 0;
            if (nearBand()) {
                stableSuccesses++;
                if (stableSuccesses >= COOLDOWN_STABLE_SUCCESSES) {
                    cooldown = Math.max(COOLDOWN_MIN, cooldown - 1);
                    stableSuccesses = 0;
                }
            } else {
                stableSuccesses = 0;
                searchHigh = cooldown;
                cooldown = Math.max(searchLow, (searchLow + searchHigh) / 2);
                cooldown = Math.max(COOLDOWN_MIN, Math.min(COOLDOWN_MAX, cooldown));
            }
            cooldownUntil = -1L;
        }

        void onPushFail(long gameTick) {
            if (cooldownUntil < 0) {
                cooldownUntil = gameTick + cooldown;
                stableSuccesses = 0;
                probedThisCycle = false;
                probeSkipCounter++;
                return;
            }
            stableSuccesses = 0;
            if (nearBand()) {
                failureStreak++;
                int step = Math.min(2, 1 + (failureStreak - 1) / 4);
                cooldown = Math.min(COOLDOWN_MAX, cooldown + step);
            } else {
                failureStreak = 0;
                searchLow = cooldown + 1;
                if (searchLow > searchHigh) {
                    searchLow = COOLDOWN_MAX;
                    searchHigh = COOLDOWN_MAX;
                    cooldown = COOLDOWN_MAX;
                } else {
                    cooldown = (searchLow + searchHigh) / 2;
                    cooldown = Math.max(
                            COOLDOWN_MIN, Math.min(COOLDOWN_MAX, cooldown));
                }
            }
            cooldownUntil = gameTick + cooldown;
            probedThisCycle = false;
            probeSkipCounter++;
        }

        private boolean nearBand() {
            return searchHigh - searchLow <= COOLDOWN_NEAR_BAND;
        }

        @Override
        public boolean isQueued() {
            return queued;
        }

        @Override
        public void setQueued(boolean queued) {
            this.queued = queued;
        }
    }

    private static final class Penalty {
        private final TransferPollSchedule schedule = new TransferPollSchedule();
        private long lastActivityTick;
        private long retryAfter;
    }

    @FunctionalInterface
    interface BatchAttempt {
        BatchAttemptResult push(
                WirelessConnection connection,
                long maxCopies,
                boolean exploratoryAttempt,
                boolean preserveBatchHistoryOnRejection);
    }

    record BatchAttemptResult(
            long ownedCopies,
            int attemptedCopies,
            boolean acceptedFullChunk,
            boolean requestLimited,
            ProviderTarget.BaselineStatus baselineStatus,
            WirelessPushOutcome outcome) {
    }

    @FunctionalInterface
    interface SingleAttempt {
        WirelessPushOutcome push(WirelessConnection connection);
    }

    /** Ready-queue ownership is local because queued state belongs to dispatch. */
    static final class ReadyQueue<T, S extends ReadyQueue.State> {
        interface State {
            boolean isQueued();

            void setQueued(boolean queued);
        }

        private final ArrayDeque<T> queue = new ArrayDeque<>();
        private final Function<T, S> stateLookup;

        ReadyQueue(Function<T, S> stateLookup) {
            this.stateLookup = stateLookup;
        }

        boolean offer(T target) {
            var state = stateLookup.apply(target);
            if (state == null || state.isQueued()) {
                return false;
            }
            state.setQueued(true);
            queue.addLast(target);
            return true;
        }

        T peek() {
            return queue.peekFirst();
        }

        T removeHead() {
            var target = queue.pollFirst();
            if (target != null) {
                var state = stateLookup.apply(target);
                if (state != null) {
                    state.setQueued(false);
                }
            }
            return target;
        }

        T rotateHeadToTail() {
            var target = queue.pollFirst();
            if (target != null) {
                queue.addLast(target);
            }
            return target;
        }

        boolean remove(T target) {
            if (!queue.remove(target)) {
                return false;
            }
            var state = stateLookup.apply(target);
            if (state != null) {
                state.setQueued(false);
            }
            return true;
        }

        boolean isEmpty() {
            return queue.isEmpty();
        }

        int size() {
            return queue.size();
        }

        void clear() {
            queue.clear();
        }

        void clear(Collection<S> knownStates) {
            queue.clear();
            for (var state : knownStates) {
                state.setQueued(false);
            }
        }
    }
}
