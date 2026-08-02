package com.moakiee.ae2lt.logic;

import java.util.HashMap;
import java.util.Map;

import appeng.api.crafting.IPatternDetails;

/**
 * Learns a pattern-scoped refill interval from an actual rejected proven
 * chunk followed by recovery. Once stable, it probes one tick earlier:
 * repeated successful probes lower the interval while a rejected probe leaves
 * both the learned interval and the target's proven batch capacity intact.
 */
final class WirelessBatchCadence<T> {
    static final int MAX_COVERAGE_TICKS = 100;
    private static final int HISTORY_TTL = 100;
    private static final int MAX_FAILURE_PRESSURE = 8;
    private static final int HIGH_FAILURE_PRESSURE = 7;
    private static final int MODERATE_FAILURE_PRESSURE = 2;
    private static final int CONFIRMED_COVERAGE_PRESSURE_DECAY = 3;
    private final Map<T, Map<IPatternDetails, State>> states = new HashMap<>();

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
        if (ownedCopies <= 0L) {
            throw new IllegalArgumentException(
                    "Successful cadence samples must own at least one copy");
        }
        var state = state(target, pattern);
        state.expireIfIdle(gameTick);
        boolean confirmedFasterCoverage = false;
        int coverage = 1;
        if (acceptedFullChunk
                && !requestLimited
                && state.lastSuccessTick != Long.MIN_VALUE
                && ownedCopies == state.lastOwnedCopies) {
            long elapsed = Math.max(1L, gameTick - state.lastSuccessTick);
            if (exploratoryAttempt) {
                if (++state.exploratorySuccesses >= 2) {
                    state.learnedCoverage = Math.max(
                            1, state.learnedCoverage - 1);
                    state.exploratorySuccesses = 0;
                    confirmedFasterCoverage = true;
                }
                elapsed = state.learnedCoverage;
            } else if (state.exploratoryProbeRejected) {
                state.exploratorySuccesses = 0;
                elapsed = state.learnedCoverage;
            } else if (state.growthProbeRejected
                    && state.provenChunkRejections == 0) {
                elapsed = 1L;
            } else if (state.provenChunkRejections > 0) {
                state.learnedCoverage = (int) Math.clamp(
                        elapsed, 1L, MAX_COVERAGE_TICKS);
            } else {
                elapsed = state.learnedCoverage;
            }
            coverage = (int) Math.min(MAX_COVERAGE_TICKS, elapsed);
        } else if (acceptedFullChunk
                && !requestLimited
                && state.provenChunkRejections > 0
                && state.lastOwnedCopies > 0L) {
            long scaledCoverage = ownedCopies * state.learnedCoverage;
            coverage = (int) Math.clamp(
                    ceilingDivide(scaledCoverage, state.lastOwnedCopies),
                    1L,
                    MAX_COVERAGE_TICKS);
        }

        int pressureFloor = pressureFloor(state.failurePressure);
        coverage = Math.max(coverage, pressureFloor);
        state.nextAttemptExploratory = false;
        if (acceptedFullChunk
                && !requestLimited
                && state.lastSuccessTick != Long.MIN_VALUE
                && ownedCopies == state.lastOwnedCopies
                && state.learnedCoverage > 1) {
            int exploratoryCoverage = Math.max(
                    pressureFloor, state.learnedCoverage - 1);
            if (exploratoryCoverage < state.learnedCoverage) {
                coverage = exploratoryCoverage;
                state.nextAttemptExploratory = true;
            }
        }
        state.failurePressure = Math.max(
                0,
                state.failurePressure
                        - (confirmedFasterCoverage
                                ? CONFIRMED_COVERAGE_PRESSURE_DECAY
                                : 1));
        state.lastSuccessTick = gameTick;
        state.lastOwnedCopies = ownedCopies;
        state.growthProbeRejected = false;
        state.provenChunkRejections = 0;
        state.exploratoryProbeRejected = false;
        return coverage;
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
        state.nextAttemptExploratory = false;
        if (exploratoryAttempt) {
            state.exploratorySuccesses = 0;
            state.exploratoryProbeRejected = true;
            return 1;
        }
        state.exploratoryProbeRejected = false;
        state.exploratorySuccesses = 0;
        if (state.lastOwnedCopies > 0L
                && attemptedCopies > state.lastOwnedCopies) {
            state.growthProbeRejected = true;
            state.failurePressure = Math.min(
                    MAX_FAILURE_PRESSURE, state.failurePressure + 1);
            return 1;
        }
        state.provenChunkRejections++;
        state.failurePressure = Math.min(
                MAX_FAILURE_PRESSURE, state.failurePressure + 2);
        return 1;
    }

    boolean isExploratoryAttempt(T target, IPatternDetails pattern) {
        var byPattern = states.get(target);
        if (byPattern == null) {
            return false;
        }
        var state = byPattern.get(pattern);
        return state != null && state.nextAttemptExploratory;
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

    private static long ceilingDivide(long amount, long divisor) {
        return amount <= 0L
                ? 0L
                : 1L + (amount - 1L) / Math.max(1L, divisor);
    }

    private static int pressureFloor(int failurePressure) {
        if (failurePressure >= HIGH_FAILURE_PRESSURE) {
            return 3;
        }
        return failurePressure >= MODERATE_FAILURE_PRESSURE ? 2 : 1;
    }

    private static final class State {
        private long lastSuccessTick = Long.MIN_VALUE;
        private long lastOwnedCopies;
        private int learnedCoverage = 1;
        private int provenChunkRejections;
        private int failurePressure;
        private boolean growthProbeRejected;
        private boolean nextAttemptExploratory;
        private boolean exploratoryProbeRejected;
        private int exploratorySuccesses;

        private void expireIfIdle(long gameTick) {
            if (lastSuccessTick == Long.MIN_VALUE) {
                return;
            }
            if (gameTick < lastSuccessTick
                    || gameTick - lastSuccessTick >= HISTORY_TTL) {
                lastSuccessTick = Long.MIN_VALUE;
                lastOwnedCopies = 0L;
                learnedCoverage = 1;
                provenChunkRejections = 0;
                failurePressure = 0;
                growthProbeRejected = false;
                nextAttemptExploratory = false;
                exploratoryProbeRejected = false;
                exploratorySuccesses = 0;
            }
        }
    }
}
