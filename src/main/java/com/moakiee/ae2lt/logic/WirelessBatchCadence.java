package com.moakiee.ae2lt.logic;

import java.util.HashMap;
import java.util.Map;

import appeng.api.crafting.IPatternDetails;

/**
 * Learns a pattern-scoped refill interval from an actual rejected proven
 * chunk followed by recovery. Once stable, it probes one tick earlier:
 * repeated successful probes lower the interval while a rejected probe leaves
 * both the learned interval and the target's proven batch capacity intact.
 * If a substantial proven chunk remains under sustained rejection without an
 * unrestricted acceptance for 100 ticks, a bounded fallback visits it at most
 * four times per 100 ticks until two consecutive successes prove that normal
 * cadence learning can safely resume. Five equal first-chunk successes followed
 * by equal second-chunk rejections form a reservoir refill proof: steady state
 * then sends one proven chunk at the observed drain interval. Successful early
 * probes shorten that interval, while two rejected early probes settle a fixed
 * cadence. An unexpected regular rejection reopens learning. Small chunks remain
 * on the original learned cadence so ordinary slow machines are not overfilled.
 */
final class WirelessBatchCadence<T> {
    static final int MAX_COVERAGE_TICKS = 100;
    private static final int HISTORY_TTL = 100;
    private static final int FILL_FALLBACK_BASELINE_TICKS = 100;
    private static final int FILL_FALLBACK_RETRY_TICKS = 25;
    private static final int FILL_FALLBACK_RECOVERY_SUCCESSES = 2;
    private static final int FILL_FALLBACK_MIN_REJECTIONS = 64;
    private static final int FILL_FALLBACK_MIN_BATCH_COPIES = 64;
    private static final int STABLE_PREFIX_SAMPLES = 5;
    private static final int SINGLE_CHUNK_SETTLE_REJECTIONS = 2;
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
                false,
                ProviderTarget.BaselineStatus.NONE);
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
                ProviderTarget.BaselineStatus.NONE);
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
        if (ownedCopies <= 0L) {
            throw new IllegalArgumentException(
                    "Successful cadence samples must own at least one copy");
        }
        var state = state(target, pattern);
        state.expireIfIdle(gameTick);
        state.lastActivityTick = gameTick;
        boolean capacitySuccess = acceptedFullChunk && !requestLimited;
        if (state.singleChunkRefill
                && baselineStatus == ProviderTarget.BaselineStatus.NONE
                && capacitySuccess) {
            finishBaselineSample(state, gameTick, ownedCopies, true);
            return state.recordSingleChunkSuccess(exploratoryAttempt);
        }
        if (state.fillFallback) {
            if (!capacitySuccess) {
                state.fillFallbackSuccesses = 0;
                return FILL_FALLBACK_RETRY_TICKS;
            }
            state.lastSuccessTick = gameTick;
            state.lastOwnedCopies = ownedCopies;
            state.lastCapacitySuccessTick = gameTick;
            state.fillFallbackRejections = 0;
            state.firstProvenRejectionTick = Long.MIN_VALUE;
            if (++state.fillFallbackSuccesses
                    < FILL_FALLBACK_RECOVERY_SUCCESSES) {
                return FILL_FALLBACK_RETRY_TICKS;
            }
            state.fillFallback = false;
            state.fillFallbackSuccesses = 0;
            state.learnedCoverage = 1;
            state.nextAttemptExploratory = false;
            state.exploratorySuccesses = 0;
            state.exploratoryProbeRejected = false;
            state.growthProbeRejected = false;
            state.provenChunkRejections = 0;
            state.failurePressure = 0;
            return 1;
        }
        if (!capacitySuccess) {
            if (shouldEnterFillFallback(state, gameTick)) {
                return enterFillFallback(state);
            }
        }
        if (baselineStatus != ProviderTarget.BaselineStatus.NONE) {
            return recordBaselineResult(
                    state,
                    gameTick,
                    ownedCopies,
                    baselineStatus);
        }
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
                && !state.fillFallback
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
        if (capacitySuccess) {
            state.lastCapacitySuccessTick = gameTick;
            state.fillFallbackRejections = 0;
            state.firstProvenRejectionTick = Long.MIN_VALUE;
        }
        state.exploratoryProbeRejected = false;
        return coverage;
    }

    int recordFailure(
            T target,
            IPatternDetails pattern,
            long gameTick,
            int attemptedCopies) {
        return recordFailure(
                target,
                pattern,
                gameTick,
                attemptedCopies,
                false);
    }

    int recordFailure(
            T target,
            IPatternDetails pattern,
            long gameTick,
            int attemptedCopies,
            boolean exploratoryAttempt) {
        var state = state(target, pattern);
        state.expireIfIdle(gameTick);
        state.lastActivityTick = gameTick;
        state.nextAttemptExploratory = false;
        if (state.fillFallback) {
            state.exploratorySuccesses = 0;
            state.exploratoryProbeRejected = false;
            state.fillFallbackSuccesses = 0;
            return FILL_FALLBACK_RETRY_TICKS;
        }
        if (state.singleChunkRefill) {
            if (exploratoryAttempt) {
                state.exploratorySuccesses = 0;
                state.exploratoryProbeRejected = true;
                if (++state.singleChunkRejectedProbes
                        >= SINGLE_CHUNK_SETTLE_REJECTIONS) {
                    state.singleChunkProbeSettled = true;
                }
            } else {
                state.exploratorySuccesses = 0;
                state.exploratoryProbeRejected = false;
                state.singleChunkCoverage = Math.min(
                        MAX_COVERAGE_TICKS,
                        state.singleChunkCoverage + 1);
                state.singleChunkRejectedProbes = 0;
                state.singleChunkProbeSettled = false;
            }
            // A probe is intentionally one tick early. Retry at the original
            // due tick; an unexpected ordinary rejection also receives this
            // cheap one-tick recheck before its enlarged interval is used.
            return 1;
        }
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
        if (state.firstProvenRejectionTick == Long.MIN_VALUE) {
            state.firstProvenRejectionTick = gameTick;
        }
        if (state.fillFallbackRejections < Integer.MAX_VALUE) {
            state.fillFallbackRejections++;
        }
        if (shouldEnterFillFallback(state, gameTick)) {
            return enterFillFallback(state);
        }
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

    boolean isFillFallback(T target, IPatternDetails pattern) {
        var byPattern = states.get(target);
        if (byPattern == null) {
            return false;
        }
        var state = byPattern.get(pattern);
        return state != null && state.fillFallback;
    }

    boolean usesSingleChunkRefill(T target, IPatternDetails pattern) {
        var byPattern = states.get(target);
        if (byPattern == null) {
            return false;
        }
        var state = byPattern.get(pattern);
        return state != null && state.singleChunkRefill;
    }

    boolean shouldPreserveBatchHistory(
            T target, IPatternDetails pattern, long gameTick) {
        var byPattern = states.get(target);
        if (byPattern == null) {
            return false;
        }
        var state = byPattern.get(pattern);
        if (state == null) {
            return false;
        }
        state.expireIfIdle(gameTick);
        return state.fillFallback
                || state.singleChunkRefill
                || state.nextAttemptExploratory
                || shouldEnterFillFallback(state, gameTick);
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

    private static int recordBaselineResult(
            State state,
            long gameTick,
            long ownedCopies,
            ProviderTarget.BaselineStatus baselineStatus) {
        long elapsed = state.lastSuccessTick == Long.MIN_VALUE
                ? 1L
                : Math.max(1L, gameTick - state.lastSuccessTick);
        if (baselineStatus
                == ProviderTarget.BaselineStatus.GROWTH_COMPLETE) {
            state.baselineCoverage = 1;
        } else if ((baselineStatus
                                == ProviderTarget.BaselineStatus.COMPLETE
                        || baselineStatus
                                == ProviderTarget.BaselineStatus.PREFIX_COMPLETE)
                && state.lastSuccessTick != Long.MIN_VALUE
                && ownedCopies == state.lastOwnedCopies) {
            state.baselineCoverage = (int) Math.clamp(
                    gameTick - state.lastSuccessTick,
                    1L,
                    MAX_COVERAGE_TICKS);
        }
        if (baselineStatus
                == ProviderTarget.BaselineStatus.PREFIX_COMPLETE) {
            state.observeStablePrefix(ownedCopies, elapsed);
        } else if (baselineStatus
                == ProviderTarget.BaselineStatus.COMPLETE
                || baselineStatus
                        == ProviderTarget.BaselineStatus.GROWTH_COMPLETE) {
            state.clearStablePrefix();
        }
        finishBaselineSample(state, gameTick, ownedCopies, true);
        state.nextAttemptExploratory = false;
        state.exploratorySuccesses = 0;
        state.exploratoryProbeRejected = false;
        return state.singleChunkRefill
                ? state.singleChunkCoverage
                : state.baselineCoverage;
    }

    private static void finishBaselineSample(
            State state,
            long gameTick,
            long ownedCopies,
            boolean capacitySuccess) {
        state.nextAttemptExploratory = false;
        state.growthProbeRejected = false;
        state.provenChunkRejections = 0;
        state.failurePressure = 0;
        state.lastSuccessTick = gameTick;
        state.lastOwnedCopies = ownedCopies;
        if (capacitySuccess) {
            state.lastCapacitySuccessTick = gameTick;
            state.fillFallbackRejections = 0;
            state.firstProvenRejectionTick = Long.MIN_VALUE;
        }
    }

    private static boolean shouldEnterFillFallback(
            State state, long gameTick) {
        if (state.fillFallbackRejections
                < FILL_FALLBACK_MIN_REJECTIONS
                || state.lastOwnedCopies
                        < FILL_FALLBACK_MIN_BATCH_COPIES) {
            return false;
        }
        long noCapacitySuccessSince =
                state.lastCapacitySuccessTick != Long.MIN_VALUE
                        ? state.lastCapacitySuccessTick
                        : state.firstProvenRejectionTick;
        return noCapacitySuccessSince != Long.MIN_VALUE
                && gameTick - noCapacitySuccessSince >=
                        FILL_FALLBACK_BASELINE_TICKS;
    }

    private static int enterFillFallback(State state) {
        state.fillFallback = true;
        state.fillFallbackSuccesses = 0;
        state.learnedCoverage = FILL_FALLBACK_BASELINE_TICKS;
        state.nextAttemptExploratory = false;
        state.exploratorySuccesses = 0;
        state.exploratoryProbeRejected = false;
        return FILL_FALLBACK_RETRY_TICKS;
    }

    private static int pressureFloor(int failurePressure) {
        if (failurePressure >= HIGH_FAILURE_PRESSURE) {
            return 3;
        }
        return failurePressure >= MODERATE_FAILURE_PRESSURE ? 2 : 1;
    }

    private static final class State {
        private long lastSuccessTick = Long.MIN_VALUE;
        private long lastCapacitySuccessTick = Long.MIN_VALUE;
        private long lastOwnedCopies;
        private int learnedCoverage = 1;
        private int provenChunkRejections;
        private int failurePressure;
        private boolean growthProbeRejected;
        private boolean nextAttemptExploratory;
        private boolean exploratoryProbeRejected;
        private int exploratorySuccesses;
        private boolean fillFallback;
        private int fillFallbackSuccesses;
        private long lastActivityTick = Long.MIN_VALUE;
        private long firstProvenRejectionTick = Long.MIN_VALUE;
        private int fillFallbackRejections;
        private int baselineCoverage = 1;
        private long stablePrefixCopies;
        private int stablePrefixCoverage;
        private int stablePrefixSamples;
        private boolean singleChunkRefill;
        private int singleChunkCoverage = 1;
        private int singleChunkRejectedProbes;
        private boolean singleChunkProbeSettled;

        private int recordSingleChunkSuccess(boolean exploratoryAttempt) {
            nextAttemptExploratory = false;
            if (exploratoryAttempt) {
                exploratoryProbeRejected = false;
                exploratorySuccesses = 0;
                singleChunkRejectedProbes = 0;
                singleChunkProbeSettled = false;
                singleChunkCoverage = Math.max(
                        1, singleChunkCoverage - 1);
                return singleChunkCoverage;
            }

            exploratorySuccesses = 0;
            if (exploratoryProbeRejected) {
                exploratoryProbeRejected = false;
                return singleChunkCoverage;
            }
            if (!singleChunkProbeSettled
                    && singleChunkCoverage > 1) {
                nextAttemptExploratory = true;
                return singleChunkCoverage - 1;
            }
            return singleChunkCoverage;
        }

        private void observeStablePrefix(long ownedCopies, long elapsed) {
            int coverage = (int) Math.clamp(
                    elapsed, 1L, MAX_COVERAGE_TICKS);
            if (stablePrefixCopies == ownedCopies
                    && stablePrefixCoverage == coverage) {
                if (stablePrefixSamples < Integer.MAX_VALUE) {
                    stablePrefixSamples++;
                }
            } else {
                stablePrefixCopies = ownedCopies;
                stablePrefixCoverage = coverage;
                stablePrefixSamples = 1;
            }
            if (stablePrefixSamples >= STABLE_PREFIX_SAMPLES) {
                singleChunkRefill = true;
                singleChunkCoverage = coverage;
            }
        }

        private void clearStablePrefix() {
            stablePrefixCopies = 0L;
            stablePrefixCoverage = 0;
            stablePrefixSamples = 0;
            singleChunkRefill = false;
            singleChunkCoverage = 1;
            singleChunkRejectedProbes = 0;
            singleChunkProbeSettled = false;
        }

        private void expireIfIdle(long gameTick) {
            if (lastActivityTick == Long.MIN_VALUE) {
                return;
            }
            if (gameTick < lastActivityTick
                    || gameTick - lastActivityTick > HISTORY_TTL) {
                lastSuccessTick = Long.MIN_VALUE;
                lastCapacitySuccessTick = Long.MIN_VALUE;
                lastOwnedCopies = 0L;
                learnedCoverage = 1;
                provenChunkRejections = 0;
                failurePressure = 0;
                growthProbeRejected = false;
                nextAttemptExploratory = false;
                exploratoryProbeRejected = false;
                exploratorySuccesses = 0;
                fillFallback = false;
                fillFallbackSuccesses = 0;
                lastActivityTick = Long.MIN_VALUE;
                firstProvenRejectionTick = Long.MIN_VALUE;
                fillFallbackRejections = 0;
                baselineCoverage = 1;
                clearStablePrefix();
            }
        }
    }
}
