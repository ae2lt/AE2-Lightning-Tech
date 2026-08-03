package com.moakiee.ae2lt.logic;

import java.util.HashMap;
import java.util.Map;

import appeng.api.crafting.IPatternDetails;

/**
 * Learns a refill cadence from the amount a target accepted.
 *
 * <p>A successful visit gives two useful facts: how many copies were drained
 * since the previous success and how long that drain took. Together with the
 * largest observed fill this estimates the time required to empty the target.
 * The next visit probes at one quarter of that interval. A rejected early
 * probe waits out the rest of the predicted interval. This
 * keeps the state bounded and reacts to both capacity and processing-speed
 * changes without retaining a separate mode for every workload shape.</p>
 *
 * <p>Physical batch safety remains owned by {@link ProviderTarget}; cadence
 * controls only when the target is revisited.</p>
 */
final class WirelessBatchCadence<T> {
    static final int MAX_COVERAGE_TICKS = 100;
    private static final int HISTORY_TTL = 100;

    private final Map<T, Map<IPatternDetails, State>> states =
            new HashMap<>();

    int recordSuccess(
            T target,
            IPatternDetails pattern,
            long gameTick,
            long ownedCopies,
            boolean acceptedFullChunk,
            boolean requestLimited) {
        return recordSuccess(
                target,
                pattern,
                gameTick,
                ownedCopies,
                acceptedFullChunk,
                requestLimited,
                false,
                ProviderTarget.BaselineStatus.NONE,
                false);
    }

    int recordSuccess(
            T target,
            IPatternDetails pattern,
            long gameTick,
            long ownedCopies,
            boolean acceptedFullChunk,
            boolean requestLimited,
            boolean exploratoryAttempt) {
        return recordSuccess(
                target,
                pattern,
                gameTick,
                ownedCopies,
                acceptedFullChunk,
                requestLimited,
                exploratoryAttempt,
                ProviderTarget.BaselineStatus.NONE,
                false);
    }

    int recordSuccess(
            T target,
            IPatternDetails pattern,
            long gameTick,
            long ownedCopies,
            boolean acceptedFullChunk,
            boolean requestLimited,
            boolean exploratoryAttempt,
            ProviderTarget.BaselineStatus baselineStatus) {
        return recordSuccess(
                target,
                pattern,
                gameTick,
                ownedCopies,
                acceptedFullChunk,
                requestLimited,
                exploratoryAttempt,
                baselineStatus,
                false);
    }

    int recordSuccess(
            T target,
            IPatternDetails pattern,
            long gameTick,
            long ownedCopies,
            boolean acceptedFullChunk,
            boolean requestLimited,
            boolean exploratoryAttempt,
            ProviderTarget.BaselineStatus baselineStatus,
            boolean reservoirBatch) {
        if (ownedCopies <= 0L) {
            throw new IllegalArgumentException(
                    "Successful cadence samples must own at least one copy");
        }

        var state = state(target, pattern);
        state.expireIfIdle(gameTick);
        trace(target, gameTick, "OK " + ownedCopies, state);
        if (state.lastSuccessTick != Long.MIN_VALUE) {
            long elapsed = Math.max(1L, gameTick - state.lastSuccessTick);
            boolean capacityIncreased = ownedCopies > state.capacityEstimate;
            boolean drainIncreased = state.lastOwnedCopies > 0L
                    && ownedCopies > state.lastOwnedCopies
                    && ownedCopies - state.lastOwnedCopies
                            >= (state.lastOwnedCopies + 1L) / 2L;
            state.capacityEstimate = Math.max(
                    state.capacityEstimate, ownedCopies);
            if (capacityIncreased || drainIncreased) {
                state.rapidSamples = 4;
            }
            int candidate = estimateFullInterval(
                    state.capacityEstimate, elapsed, ownedCopies);
            if (state.rapidSamples > 0) {
                state.fullInterval = candidate;
                state.fasterCandidate = 0;
                state.rapidSamples--;
            } else if (candidate * 4L <= state.fullInterval * 3L) {
                if (state.fasterCandidate > 0
                        && candidate <= state.fasterCandidate * 2L
                        && state.fasterCandidate <= candidate * 2L) {
                    state.fullInterval = candidate;
                    state.fasterCandidate = 0;
                } else {
                    state.fasterCandidate = candidate;
                }
            } else {
                state.fullInterval = candidate;
                state.fasterCandidate = 0;
            }
        } else {
            state.capacityEstimate = ownedCopies;
            state.fullInterval = 1;
        }

        state.lastSuccessTick = gameTick;
        state.lastActivityTick = gameTick;
        state.lastOwnedCopies = ownedCopies;
        state.rejectedElapsed = 0;
        state.nextInterval = probeDelay(state.fullInterval);
        return state.nextInterval;
    }

    int recordFailure(
            T target,
            IPatternDetails pattern,
            long gameTick,
            int attemptedCopies) {
        return recordFailure(
                target, pattern, gameTick, attemptedCopies, false);
    }

    int recordFailure(
            T target,
            IPatternDetails pattern,
            long gameTick,
            int attemptedCopies,
            boolean exploratoryAttempt) {
        var state = state(target, pattern);
        state.expireIfIdle(gameTick);
        trace(target, gameTick, "NO " + attemptedCopies, state);
        state.lastActivityTick = gameTick;
        state.fasterCandidate = 0;

        if (state.lastSuccessTick == Long.MIN_VALUE) {
            state.nextInterval = Math.min(
                    MAX_COVERAGE_TICKS,
                    Math.max(1, state.nextInterval * 2));
            return state.nextInterval;
        }

        int elapsed = (int) Math.clamp(
                gameTick - state.lastSuccessTick,
                1L,
                MAX_COVERAGE_TICKS);
        state.rejectedElapsed = Math.max(state.rejectedElapsed, elapsed);
        if (state.rejectedElapsed >= state.fullInterval
                && state.fullInterval < MAX_COVERAGE_TICKS) {
            state.fullInterval = Math.min(
                    MAX_COVERAGE_TICKS,
                    Math.max(
                            state.rejectedElapsed + 1,
                            state.fullInterval * 2));
        }
        state.nextInterval = state.rejectedElapsed >= state.fullInterval
                ? state.fullInterval
                : state.fullInterval - elapsed;
        return state.nextInterval;
    }

    boolean isExploratoryAttempt(T target, IPatternDetails pattern) {
        return existingState(target, pattern) != null;
    }

    boolean shouldReopenReservoirTail(
            T target, IPatternDetails pattern) {
        return existingState(target, pattern) != null;
    }

    boolean isFillFallback(T target, IPatternDetails pattern) {
        return false;
    }

    boolean usesSingleChunkRefill(T target, IPatternDetails pattern) {
        return false;
    }

    boolean usesProvenChunkProbe(T target, IPatternDetails pattern) {
        return false;
    }

    long reservoirAllowance(T target, IPatternDetails pattern) {
        return Long.MAX_VALUE;
    }

    boolean shouldPreserveBatchHistory(
            T target, IPatternDetails pattern, long gameTick) {
        var state = existingState(target, pattern);
        if (state == null) {
            return false;
        }
        state.expireIfIdle(gameTick);
        return state.lastActivityTick != Long.MIN_VALUE;
    }

    void removeTarget(T target) {
        states.remove(target);
    }

    void clear() {
        states.clear();
    }

    private State state(T target, IPatternDetails pattern) {
        var byPattern = states.computeIfAbsent(
                target, ignored -> CanonicalPatternMaps.create());
        return byPattern.computeIfAbsent(pattern, ignored -> new State());
    }

    private State existingState(T target, IPatternDetails pattern) {
        var byPattern = states.get(target);
        return byPattern == null ? null : byPattern.get(pattern);
    }

    private static int estimateFullInterval(
            long capacity, long elapsed, long accepted) {
        long quotient;
        if (elapsed > Long.MAX_VALUE / capacity) {
            quotient = Long.MAX_VALUE;
        } else {
            long product = elapsed * capacity;
            quotient = product / accepted
                    + (product % accepted == 0L ? 0L : 1L);
        }
        return (int) Math.clamp(
                quotient, 1L, MAX_COVERAGE_TICKS);
    }

    private static int probeDelay(int fullInterval) {
        return Math.max(1, (fullInterval + 3) / 4);
    }

    private static void trace(
            Object target, long tick, String event, State state) {
        if (target instanceof ProviderTarget providerTarget
                && providerTarget.pos().getX() == 0
                && tick >= 450 && tick < 820) {
            System.err.println("t=" + tick + " " + event
                    + " last=" + state.lastSuccessTick
                    + " cap=" + state.capacityEstimate
                    + " full=" + state.fullInterval
                    + " rejected=" + state.rejectedElapsed
                    + " next=" + state.nextInterval);
        }
    }

    private static final class State {
        private long lastSuccessTick = Long.MIN_VALUE;
        private long lastActivityTick = Long.MIN_VALUE;
        private long capacityEstimate;
        private long lastOwnedCopies;
        private int fullInterval = 1;
        private int fasterCandidate;
        private int rapidSamples;
        private int rejectedElapsed;
        private int nextInterval = 1;

        private void expireIfIdle(long gameTick) {
            if (lastActivityTick == Long.MIN_VALUE) {
                return;
            }
            if (gameTick < lastActivityTick
                    || gameTick - lastActivityTick > HISTORY_TTL) {
                lastSuccessTick = Long.MIN_VALUE;
                lastActivityTick = Long.MIN_VALUE;
                capacityEstimate = 0L;
                lastOwnedCopies = 0L;
                fullInterval = 1;
                fasterCandidate = 0;
                rapidSamples = 0;
                rejectedElapsed = 0;
                nextInterval = 1;
            }
        }
    }
}
