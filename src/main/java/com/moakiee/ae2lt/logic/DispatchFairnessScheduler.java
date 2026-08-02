package com.moakiee.ae2lt.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;

/**
 * Runtime-only, pattern-scoped fair target scheduler.
 *
 * <p>Successfully owned pattern copies are counted in 100-tick accounting windows.
 * A pass always leases the least-served active targets first and leases every
 * target at most once. Targets in rejection cooldown or provider-owned overflow
 * are excluded from both the active average and the priority queue.
 *
 * <p>A target that becomes eligible midway through a window starts at the
 * current active average. This is deliberately a scheduling credit rather than
 * a fabricated physical dispatch: unavailable machines do not accumulate debt
 * that would otherwise monopolize all work when they return.
 */
final class DispatchFairnessScheduler<T, P> {

    static final int WINDOW_TICKS = 100;
    private static final int MIN_READY_COMPACTION_SIZE = 64;
    private static final int READY_COMPACTION_MULTIPLIER = 4;

    private final Map<P, PatternState<T>> patterns;
    private final Set<T> pausedTargets = new HashSet<>();
    private long sequence;

    DispatchFairnessScheduler() {
        this(new HashMap<>());
    }

    private DispatchFairnessScheduler(Map<P, PatternState<T>> patterns) {
        this.patterns = patterns;
    }

    static <T> DispatchFairnessScheduler<T, IPatternDetails>
            forCanonicalPatterns() {
        return new DispatchFairnessScheduler<>(
                CanonicalPatternMaps.create());
    }

    Pass beginPass(
            P pattern,
            Collection<T> currentTargets,
            long topologyVersion,
            long gameTick) {
        var state = stateFor(pattern, gameTick);
        synchronizeTargets(state, currentTargets, topologyVersion);
        activateDueTargets(state, gameTick);
        if (state.passOpen) {
            throw new IllegalStateException("A fairness pass is already open for this pattern");
        }
        state.passOpen = true;
        return new Pass(state, gameTick);
    }

    void excludeUntil(
            P pattern,
            T target,
            long retryAfter,
            long gameTick) {
        var patternState = stateFor(pattern, gameTick);
        var targetState = ensureTarget(patternState, target);
        deactivate(patternState, targetState);
        targetState.cooldownUntil = Math.max(gameTick + 1L, retryAfter);
        targetState.cooldownVersion++;
        patternState.cooldowns.add(new CooldownEntry<>(
                targetState.cooldownUntil,
                targetState.cooldownVersion,
                targetState));
    }

    void pauseTarget(T target) {
        if (!pausedTargets.add(target)) {
            return;
        }
        for (var patternState : patterns.values()) {
            var targetState = patternState.targets.get(target);
            if (targetState != null) {
                deactivate(patternState, targetState);
            }
        }
    }

    void resumeTarget(T target, long gameTick) {
        if (!pausedTargets.remove(target)) {
            return;
        }
        for (var patternState : patterns.values()) {
            advanceWindow(patternState, gameTick);
            activateDueTargets(patternState, gameTick);
            var targetState = patternState.targets.get(target);
            if (targetState != null
                    && targetState.cooldownUntil <= gameTick
                    && !targetState.active) {
                activateAtCurrentAverage(patternState, targetState);
            }
        }
    }

    void removeTarget(T target) {
        pausedTargets.remove(target);
        for (var patternState : patterns.values()) {
            var targetState = patternState.targets.remove(target);
            if (targetState != null) {
                removeState(patternState, targetState);
            }
        }
    }

    void clear() {
        patterns.clear();
        pausedTargets.clear();
    }

    long dispatchCount(P pattern, T target, long gameTick) {
        var patternState = stateFor(pattern, gameTick);
        activateDueTargets(patternState, gameTick);
        var targetState = patternState.targets.get(target);
        return targetState == null ? 0L : targetState.ownedCopies;
    }

    long minimumActiveDispatchCount(P pattern, long gameTick) {
        var patternState = stateFor(pattern, gameTick);
        activateDueTargets(patternState, gameTick);
        long minimum = Long.MAX_VALUE;
        for (var targetState : patternState.targets.values()) {
            if (targetState.active) {
                minimum = Math.min(minimum, targetState.ownedCopies);
            }
        }
        return minimum == Long.MAX_VALUE ? 0L : minimum;
    }

    long maximumActiveDispatchCount(P pattern, long gameTick) {
        var patternState = stateFor(pattern, gameTick);
        activateDueTargets(patternState, gameTick);
        long maximum = 0L;
        for (var targetState : patternState.targets.values()) {
            if (targetState.active) {
                maximum = Math.max(maximum, targetState.ownedCopies);
            }
        }
        return maximum;
    }

    int activeTargetCount(P pattern, long gameTick) {
        var patternState = stateFor(pattern, gameTick);
        activateDueTargets(patternState, gameTick);
        return patternState.activeCount;
    }

    private PatternState<T> stateFor(P pattern, long gameTick) {
        var state = patterns.computeIfAbsent(pattern, ignored -> new PatternState<>());
        advanceWindow(state, gameTick);
        return state;
    }

    private void advanceWindow(PatternState<T> state, long gameTick) {
        if (state.lastAdvancedTick == gameTick) {
            return;
        }

        if (state.lastAdvancedTick != Long.MIN_VALUE
                && gameTick < state.lastAdvancedTick) {
            state.history.clear();
            state.activeSum = 0L;
            state.activeCountFrequencies.clear();
            for (var targetState : state.targets.values()) {
                targetState.ownedCopies = 0L;
                targetState.schedulingCredit = 0L;
                targetState.leased = false;
                targetState.leasedAllowance = 0L;
                targetState.queueVersion++;
                if (targetState.active) {
                    addFrequency(state, 0L);
                    offer(state, targetState);
                }
            }
        }
        state.lastAdvancedTick = gameTick;

        long expireThrough = gameTick - WINDOW_TICKS;
        while (!state.history.isEmpty()
                && state.history.peekFirst().gameTick <= expireThrough) {
            var expired = state.history.removeFirst();
            expireAmounts(state, expired.ownedCopies, false);
            expireAmounts(state, expired.schedulingCredits, true);
        }
    }

    private void expireAmounts(
            PatternState<T> state,
            Map<TargetState<T>, Long> amounts,
            boolean schedulingCredit) {
        for (var entry : amounts.entrySet()) {
            var targetState = entry.getKey();
            if (state.targets.get(targetState.target) != targetState) {
                continue;
            }
            long amount = Math.min(
                    schedulingCredit
                            ? targetState.schedulingCredit
                            : targetState.ownedCopies,
                    entry.getValue());
            if (amount <= 0L) {
                continue;
            }
            if (targetState.active) {
                removeFrequency(state, targetState.effectiveCount());
                state.activeSum -= amount;
            }
            if (schedulingCredit) {
                targetState.schedulingCredit -= amount;
            } else {
                targetState.ownedCopies -= amount;
            }
            if (targetState.active) {
                addFrequency(state, targetState.effectiveCount());
                offer(state, targetState);
            }
        }
    }

    private void synchronizeTargets(
            PatternState<T> state,
            Collection<T> currentTargets,
            long topologyVersion) {
        if (state.topologyVersion == topologyVersion) {
            return;
        }

        var retained = new HashSet<T>(currentTargets);
        var iterator = state.targets.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!retained.contains(entry.getKey())) {
                removeState(state, entry.getValue());
                iterator.remove();
            }
        }
        for (var target : currentTargets) {
            ensureTarget(state, target);
        }
        state.topologyVersion = topologyVersion;
    }

    private TargetState<T> ensureTarget(PatternState<T> state, T target) {
        var existing = state.targets.get(target);
        if (existing != null) {
            return existing;
        }

        var created = new TargetState<>(target);
        state.targets.put(target, created);
        if (!pausedTargets.contains(target)) {
            activateAtCurrentAverage(state, created);
        }
        return created;
    }

    private void activateDueTargets(PatternState<T> state, long gameTick) {
        while (!state.cooldowns.isEmpty()
                && state.cooldowns.peek().retryAfter <= gameTick) {
            var due = state.cooldowns.poll();
            var targetState = due.targetState;
            if (due.cooldownVersion != targetState.cooldownVersion
                    || due.retryAfter != targetState.cooldownUntil
                    || !state.targets.containsKey(targetState.target)
                    || pausedTargets.contains(targetState.target)
                    || targetState.active) {
                continue;
            }
            activateAtCurrentAverage(state, targetState);
        }
    }

    private void activateAtCurrentAverage(
            PatternState<T> state,
            TargetState<T> targetState) {
        long baseline = ceilingAverage(state.activeSum, state.activeCount);
        if (targetState.effectiveCount() < baseline) {
            addSchedulingCredit(
                    state, targetState, baseline - targetState.effectiveCount());
        }
        targetState.cooldownUntil = Long.MIN_VALUE;
        targetState.active = true;
        targetState.leased = false;
        state.activeCount++;
        state.activeSum = saturatingAdd(
                state.activeSum, targetState.effectiveCount());
        addFrequency(state, targetState.effectiveCount());
        offer(state, targetState);
    }

    private void deactivate(PatternState<T> state, TargetState<T> targetState) {
        if (targetState.active) {
            state.activeCount--;
            state.activeSum -= targetState.effectiveCount();
            removeFrequency(state, targetState.effectiveCount());
        }
        targetState.active = false;
        targetState.leased = false;
        targetState.leasedAllowance = 0L;
        targetState.queueVersion++;
    }

    private void removeState(PatternState<T> state, TargetState<T> targetState) {
        deactivate(state, targetState);
        targetState.cooldownVersion++;
    }

    private void addSuccessfulCopies(
            PatternState<T> state,
            TargetState<T> targetState,
            long ownedCopies) {
        removeFrequency(state, targetState.effectiveCount());
        targetState.ownedCopies = saturatingAdd(
                targetState.ownedCopies, ownedCopies);
        state.activeSum = saturatingAdd(state.activeSum, ownedCopies);
        addFrequency(state, targetState.effectiveCount());
        addHistory(state, targetState, ownedCopies, false);
        targetState.lastSuccessfulSequence = ++sequence;
    }

    private void addSchedulingCredit(
            PatternState<T> state,
            TargetState<T> targetState,
            long credit) {
        if (credit <= 0L) {
            return;
        }
        if (targetState.active) {
            removeFrequency(state, targetState.effectiveCount());
            state.activeSum = saturatingAdd(state.activeSum, credit);
        }
        targetState.schedulingCredit = saturatingAdd(
                targetState.schedulingCredit, credit);
        if (targetState.active) {
            addFrequency(state, targetState.effectiveCount());
            offer(state, targetState);
        }
        addHistory(state, targetState, credit, true);
    }

    private void addHistory(
            PatternState<T> state,
            TargetState<T> targetState,
            long amount,
            boolean schedulingCredit) {
        if (amount <= 0L) {
            return;
        }
        WindowBucket<T> bucket = state.history.peekLast();
        if (bucket == null || bucket.gameTick != state.lastAdvancedTick) {
            bucket = new WindowBucket<>(state.lastAdvancedTick);
            state.history.addLast(bucket);
        }
        var amounts = schedulingCredit
                ? bucket.schedulingCredits
                : bucket.ownedCopies;
        amounts.merge(
                targetState,
                amount,
                DispatchFairnessScheduler::saturatingAdd);
    }

    private static <T> void addFrequency(PatternState<T> state, long count) {
        state.activeCountFrequencies.merge(count, 1, Integer::sum);
    }

    private static <T> void removeFrequency(PatternState<T> state, long count) {
        var occurrences = state.activeCountFrequencies.get(count);
        if (occurrences == null) {
            return;
        }
        if (occurrences <= 1) {
            state.activeCountFrequencies.remove(count);
        } else {
            state.activeCountFrequencies.put(count, occurrences - 1);
        }
    }

    private void offer(PatternState<T> state, TargetState<T> targetState) {
        if (!targetState.active || targetState.leased) {
            return;
        }
        long version = ++targetState.queueVersion;
        state.ready.add(new ReadyEntry<>(
                targetState.effectiveCount(),
                targetState.lastSuccessfulSequence,
                ++sequence,
                version,
                targetState));
        compactReadyIfNeeded(state);
    }

    private void compactReadyIfNeeded(PatternState<T> state) {
        int threshold = Math.max(
                MIN_READY_COMPACTION_SIZE,
                state.activeCount * READY_COMPACTION_MULTIPLIER);
        if (state.ready.size() <= threshold) {
            return;
        }
        state.ready.clear();
        for (var targetState : state.targets.values()) {
            if (!targetState.active || targetState.leased) {
                continue;
            }
            long version = ++targetState.queueVersion;
            state.ready.add(new ReadyEntry<>(
                    targetState.effectiveCount(),
                    targetState.lastSuccessfulSequence,
                    ++sequence,
                    version,
                    targetState));
        }
    }

    @Nullable
    private TargetState<T> pollState(PatternState<T> state) {
        while (!state.ready.isEmpty()) {
            var entry = state.ready.poll();
            var targetState = entry.targetState;
            if (entry.queueVersion != targetState.queueVersion
                    || !targetState.active
                    || targetState.leased
                    || !state.targets.containsKey(targetState.target)) {
                continue;
            }
            targetState.leased = true;
            targetState.queueVersion++;
            return targetState;
        }
        return null;
    }

    private static long ceilingAverage(long sum, int count) {
        if (sum <= 0L || count <= 0) {
            return 0L;
        }
        return 1L + (sum - 1L) / count;
    }

    private static long doubledAverageFloor(long sum, int count) {
        if (sum <= 0L || count <= 0) {
            return 0L;
        }
        long quotient = sum / count;
        long remainder = sum % count;
        long doubledQuotient = saturatingAdd(quotient, quotient);
        long doubledRemainder = (remainder * 2L) / count;
        return saturatingAdd(doubledQuotient, doubledRemainder);
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    final class Pass implements AutoCloseable {
        private final PatternState<T> state;
        private final long gameTick;
        private final ArrayList<TargetState<T>> leased = new ArrayList<>();
        private boolean closed;

        private Pass(PatternState<T> state, long gameTick) {
            this.state = state;
            this.gameTick = gameTick;
        }

        int activeTargetsAtStart() {
            return state.activeCount;
        }

        /** Maximum pattern copies this target may own during the current pass. */
        long allowance(T target) {
            var targetState = state.targets.get(target);
            if (targetState == null || !targetState.active) {
                return 0L;
            }
            if (targetState.leased) {
                return targetState.leasedAllowance;
            }
            return allowanceFor(state, targetState);
        }

        private long allowanceFor(
                PatternState<T> patternState,
                TargetState<T> targetState) {
            long averageCeiling = doubledAverageFloor(
                    patternState.activeSum, patternState.activeCount);
            long minimum = patternState.activeCountFrequencies.isEmpty()
                    ? 0L
                    : patternState.activeCountFrequencies.firstKey();
            long ratioCeiling = saturatingAdd(
                    Math.max(1L, minimum),
                    Math.max(1L, minimum));
            long ceiling = Math.max(
                    1L,
                    Math.min(averageCeiling, ratioCeiling));
            return Math.max(0L, ceiling - targetState.effectiveCount());
        }

        @Nullable
        T poll() {
            ensureOpen();
            var targetState = pollState(state);
            if (targetState == null) {
                return null;
            }
            targetState.leasedAllowance = allowanceFor(state, targetState);
            if (targetState.leasedAllowance <= 0L) {
                targetState.leased = false;
                targetState.leasedAllowance = 0L;
                offer(state, targetState);
                return null;
            }
            leased.add(targetState);
            return targetState.target;
        }

        void success(T target, long ownedCopies) {
            ensureOpen();
            if (ownedCopies <= 0L) {
                throw new IllegalArgumentException(
                        "A successful dispatch must own at least one copy");
            }
            var targetState = requireLeased(target);
            if (ownedCopies > targetState.leasedAllowance) {
                throw new IllegalArgumentException(
                        "Successful copies exceed this target's fairness allowance");
            }
            addSuccessfulCopies(state, targetState, ownedCopies);
        }

        /**
         * Records physically owned copies and waits for the learned refill
         * interval. Keeping the observed phase is required for machines whose
         * processing completion is synchronized.
         */
        void successAndCover(
                T target,
                long ownedCopies,
                int coverageTicks) {
            success(target, ownedCopies);
            var targetState = requireLeased(target);
            int boundedCoverage = Math.max(1, coverageTicks);
            long delay = boundedCoverage;
            deactivate(state, targetState);
            targetState.cooldownUntil = saturatingAdd(gameTick, delay);
            targetState.cooldownVersion++;
            state.cooldowns.add(new CooldownEntry<>(
                    targetState.cooldownUntil,
                    targetState.cooldownVersion,
                    targetState));
        }

        void cooldown(T target, long retryAfter) {
            ensureOpen();
            var targetState = requireLeased(target);
            deactivate(state, targetState);
            targetState.cooldownUntil = Math.max(gameTick + 1L, retryAfter);
            targetState.cooldownVersion++;
            state.cooldowns.add(new CooldownEntry<>(
                    targetState.cooldownUntil,
                    targetState.cooldownVersion,
                    targetState));
        }

        void remove(T target) {
            ensureOpen();
            var targetState = requireLeased(target);
            state.targets.remove(target);
            removeState(state, targetState);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (var targetState : leased) {
                if (targetState.leased) {
                    targetState.leased = false;
                    targetState.leasedAllowance = 0L;
                    offer(state, targetState);
                }
            }
            state.passOpen = false;
        }

        private TargetState<T> requireLeased(T target) {
            var targetState = state.targets.get(target);
            if (targetState == null || !targetState.leased) {
                throw new IllegalStateException("Target is not leased by this fairness pass");
            }
            return targetState;
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Fairness pass is already closed");
            }
        }
    }

    private static final class PatternState<T> {
        private final Map<T, TargetState<T>> targets = new HashMap<>();
        private final PriorityQueue<ReadyEntry<T>> ready =
                new PriorityQueue<>(Comparator
                        .comparingLong((ReadyEntry<T> entry) -> entry.dispatchCount)
                        .thenComparingLong(entry -> entry.lastSuccessfulSequence)
                        .thenComparingLong(entry -> entry.tieSequence));
        private final PriorityQueue<CooldownEntry<T>> cooldowns =
                new PriorityQueue<>(Comparator.comparingLong(entry -> entry.retryAfter));
        private final TreeMap<Long, Integer> activeCountFrequencies = new TreeMap<>();
        private final ArrayDeque<WindowBucket<T>> history = new ArrayDeque<>();
        private long lastAdvancedTick = Long.MIN_VALUE;
        private long topologyVersion = Long.MIN_VALUE;
        private long activeSum;
        private int activeCount;
        private boolean passOpen;
    }

    private static final class TargetState<T> {
        private final T target;
        private long ownedCopies;
        private long schedulingCredit;
        private long lastSuccessfulSequence;
        private long queueVersion;
        private long cooldownVersion;
        private long cooldownUntil = Long.MIN_VALUE;
        private long leasedAllowance;
        private boolean active;
        private boolean leased;

        private TargetState(T target) {
            this.target = target;
        }

        private long effectiveCount() {
            return saturatingAdd(ownedCopies, schedulingCredit);
        }
    }

    private record ReadyEntry<T>(
            long dispatchCount,
            long lastSuccessfulSequence,
            long tieSequence,
            long queueVersion,
            TargetState<T> targetState) {
    }

    private record CooldownEntry<T>(
            long retryAfter,
            long cooldownVersion,
            TargetState<T> targetState) {
    }

    private static final class WindowBucket<T> {
        private final long gameTick;
        private final Map<TargetState<T>, Long> ownedCopies = new HashMap<>();
        private final Map<TargetState<T>, Long> schedulingCredits = new HashMap<>();

        private WindowBucket(long gameTick) {
            this.gameTick = gameTick;
        }
    }
}
