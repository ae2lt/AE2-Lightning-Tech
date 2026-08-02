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
    private static final int SINGLE_CHUNK_AUDIT_MIN_COVERAGE = 16;
    private static final int RESERVOIR_WINDOW_TICKS = 25;
    private static final int RESERVOIR_SUCCESS_SAMPLES = 5;
    private static final int RESERVOIR_RECOVERY_SUCCESS_SAMPLES = 2;
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
        state.lastActivityTick = gameTick;
        reservoirBatch = state.reservoirCadenceActive
                || reservoirBatch && usesReservoirCadence(ownedCopies);
        boolean capacitySuccess = acceptedFullChunk && !requestLimited;
        if (capacitySuccess
                && baselineStatus
                        == ProviderTarget.BaselineStatus.GROWTH_COMPLETE
                && state.stablePrefixCopies > 0L
                && ownedCopies / state.stablePrefixCopies >= 3L) {
            // A refill several times larger than the old stable prefix is a
            // direct speed-change signal. Keeping the old long interval here
            // can starve a reconfigured machine for another whole window.
            state.clearStablePrefix();
            return beginReservoirWindowLearning(
                    state, gameTick, ownedCopies);
        }
        if (state.singleChunkRefill
                && exploratoryAttempt
                && state.stablePrefixCopies > 0L
                && ownedCopies >= 2L * state.stablePrefixCopies) {
            // The exploratory visit refilled at least two proven H chunks.
            // Promote the complete owned transaction to a new sliding-window
            // candidate instead of treating it as another single-H sample.
            int previousCoverage = state.singleChunkCoverage;
            state.clearStablePrefix();
            return beginReservoirCadence(
                    state,
                    gameTick,
                    ownedCopies,
                    previousCoverage);
        }
        if (state.singleChunkRefill
                && baselineStatus == ProviderTarget.BaselineStatus.NONE
                && ownedCopies == state.stablePrefixCopies
                && capacitySuccess) {
            if (reservoirBatch
                    && exploratoryAttempt
                    && state.singleChunkFullTailAudit) {
                state.clearStablePrefix();
                return beginReservoirWindowLearning(
                        state, gameTick, ownedCopies);
            }
            boolean recoveredFromRejection = !exploratoryAttempt
                    && state.singleChunkRejectedProbes > 0;
            int observedCoverage = state.lastSuccessTick == Long.MIN_VALUE
                    ? state.singleChunkCoverage
                    : (int) Math.clamp(
                            gameTick - state.lastSuccessTick,
                            1L,
                            MAX_COVERAGE_TICKS);
            finishBaselineSample(state, gameTick, ownedCopies, true);
            if (recoveredFromRejection) {
                state.singleChunkCoverage = observedCoverage;
                state.singleChunkRejectedProbes = 0;
                state.singleChunkProbeSettled = true;
            }
            return state.recordSingleChunkSuccess(
                    exploratoryAttempt, gameTick);
        }
        if (state.singleChunkRefill
                && capacitySuccess
                && ownedCopies != state.stablePrefixCopies) {
            if (reservoirBatch
                    && state.reservoirCadenceActive
                    && ownedCopies * 4L
                            < state.reservoirCadenceCopies * 3L) {
                finishBaselineSample(
                        state, gameTick, ownedCopies, true);
                state.nextAttemptExploratory = false;
                state.singleChunkReservoirAudit = false;
                state.singleChunkFullTailAudit = false;
                return state.singleChunkCoverage;
            }
            // A formerly slow reservoir accepted a larger complete refill.
            // The old single-H cadence no longer describes this machine, so
            // return to ordinary interval learning immediately.
            int previousCoverage = state.singleChunkCoverage;
            state.clearStablePrefix();
            finishBaselineSample(state, gameTick, ownedCopies, true);
            return beginReservoirCadence(
                    state,
                    gameTick,
                    ownedCopies,
                    previousCoverage);
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
        if (reservoirBatch
                && state.reservoirCadenceActive
                && state.reservoirCadenceCopies > ownedCopies
                && (!requestLimited
                        || state.reservoirWaterlineSettled)
                && (!capacitySuccess
                        || ownedCopies * 4L
                                < state.reservoirCadenceCopies * 3L)) {
            return recordReservoirBoundary(
                    state, gameTick, ownedCopies);
        }
        if (state.reservoirCadenceActive
                && capacitySuccess
                && ownedCopies > state.reservoirCadenceCopies) {
            // A reconfigured fast target may accept progressively larger
            // complete ramps (1536, 1792, 1920, ...). Promote each observed
            // waterline immediately; otherwise the first smaller candidate
            // remains authoritative and later successes are misclassified as
            // unrelated baseline samples.
            return beginReservoirWindowLearning(
                    state, gameTick, ownedCopies);
        }
        if (reservoirBatch
                && state.reservoirCadenceActive
                && ownedCopies == state.reservoirCadenceCopies
                && (baselineStatus
                                == ProviderTarget.BaselineStatus.GROWTH_COMPLETE
                        || baselineStatus
                                == ProviderTarget.BaselineStatus.COMPLETE)) {
            // The physical ramp may reject a speculative tail after the whole
            // learned transaction has already entered. That is a successful
            // reservoir refill for cadence purposes, not a one-tick baseline.
            return recordReservoirSuccess(
                    state,
                    gameTick,
                    ownedCopies,
                    exploratoryAttempt);
        }
        if (baselineStatus != ProviderTarget.BaselineStatus.NONE) {
            return recordBaselineResult(
                    state,
                    gameTick,
                    ownedCopies,
                    baselineStatus,
                    reservoirBatch);
        }
        if (reservoirBatch && capacitySuccess) {
            return recordReservoirSuccess(
                    state,
                    gameTick,
                    ownedCopies,
                    exploratoryAttempt);
        }
        boolean confirmedFasterCoverage = false;
        int coverage = 1;
        if (acceptedFullChunk
                && !requestLimited
                && state.lastSuccessTick != Long.MIN_VALUE
                && ownedCopies == state.lastOwnedCopies) {
            long elapsed = Math.max(1L, gameTick - state.lastSuccessTick);
            if (exploratoryAttempt) {
                int requiredSuccesses = reservoirBatch ? 1 : 2;
                if (++state.exploratorySuccesses >= requiredSuccesses) {
                    state.learnedCoverage = reservoirBatch
                            ? Math.max(1, state.learnedCoverage / 2)
                            : Math.max(1, state.learnedCoverage - 1);
                    if (reservoirBatch) {
                        state.baselineCoverage = state.learnedCoverage;
                    }
                    state.exploratorySuccesses = 0;
                    confirmedFasterCoverage = true;
                    state.reservoirRejectedProbes = 0;
                    state.reservoirProbeSettled = false;
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
                && state.learnedCoverage > 1
                && !(reservoirBatch
                        && state.reservoirProbeSettled
                        && gameTick - state.lastReservoirProbeTick < 100L)) {
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
        state.reservoirBatch = reservoirBatch;
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
                boolean reservoirAudit = state.singleChunkReservoirAudit;
                state.singleChunkReservoirAudit = false;
                state.singleChunkFullTailAudit = false;
                state.exploratorySuccesses = 0;
                state.exploratoryProbeRejected = true;
                if (++state.singleChunkRejectedProbes
                        >= SINGLE_CHUNK_SETTLE_REJECTIONS) {
                    state.singleChunkProbeSettled = true;
                }
                state.lastReservoirProbeTick = gameTick;
                return reservoirAudit
                        ? state.singleChunkCoverage
                        : 1;
            } else {
                state.exploratorySuccesses = 0;
                state.exploratoryProbeRejected = true;
                state.singleChunkRejectedProbes = Math.min(
                        Integer.MAX_VALUE,
                        state.singleChunkRejectedProbes + 1);
                return rejectionBackoff(
                        state.singleChunkRejectedProbes);
            }
        }
        if (state.reservoirCadenceActive) {
            return recordReservoirFailure(state, gameTick);
        }
        if (exploratoryAttempt) {
            state.exploratorySuccesses = 0;
            state.exploratoryProbeRejected = true;
            if (state.reservoirBatch
                    && ++state.reservoirRejectedProbes >= 1) {
                state.reservoirProbeSettled = true;
                state.lastReservoirProbeTick = gameTick;
            }
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
        return Math.max(
                rejectionBackoff(state.provenChunkRejections),
                state.baselineCoverage);
    }

    boolean isExploratoryAttempt(T target, IPatternDetails pattern) {
        var byPattern = states.get(target);
        if (byPattern == null) {
            return false;
        }
        var state = byPattern.get(pattern);
        return state != null && state.nextAttemptExploratory;
    }

    boolean shouldReopenReservoirTail(
            T target, IPatternDetails pattern) {
        var byPattern = states.get(target);
        if (byPattern == null) {
            return false;
        }
        var state = byPattern.get(pattern);
        return state != null
                && state.nextAttemptExploratory
                && (state.singleChunkFullTailAudit
                        || state.reservoirFullTailAudit);
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

    boolean usesProvenChunkProbe(
            T target, IPatternDetails pattern) {
        var byPattern = states.get(target);
        if (byPattern == null) {
            return false;
        }
        var state = byPattern.get(pattern);
        return state != null
                && state.reservoirCadenceActive
                && state.nextAttemptExploratory
                && !state.reservoirFullTailAudit;
    }

    long reservoirAllowance(T target, IPatternDetails pattern) {
        var byPattern = states.get(target);
        if (byPattern == null) {
            return Long.MAX_VALUE;
        }
        var state = byPattern.get(pattern);
        if (state == null
                || !state.reservoirCadenceActive
                || !state.reservoirWaterlineSettled) {
            return Long.MAX_VALUE;
        }
        return Math.max(
                1L,
                state.reservoirCadenceCopies
                        - state.reservoirCycleOwnedCopies);
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

    private static boolean usesReservoirCadence(long copies) {
        return copies >= 1_024L && (copies & (copies - 1L)) != 0L;
    }

    private static int recordBaselineResult(
            State state,
            long gameTick,
            long ownedCopies,
            ProviderTarget.BaselineStatus baselineStatus,
            boolean reservoirBatch) {
        long elapsed = state.lastSuccessTick == Long.MIN_VALUE
                ? 1L
                : Math.max(1L, gameTick - state.lastSuccessTick);
        boolean continuesStablePrefix = baselineStatus
                        == ProviderTarget.BaselineStatus.GROWTH_COMPLETE
                && state.stablePrefixSamples > 0
                && state.stablePrefixCopies > 0L
                && ownedCopies >= state.stablePrefixCopies;
        if (baselineStatus
                == ProviderTarget.BaselineStatus.GROWTH_COMPLETE) {
            state.baselineCoverage = continuesStablePrefix
                    ? (int) Math.clamp(
                            elapsed, 1L, MAX_COVERAGE_TICKS)
                    : 1;
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
            state.observeStablePrefix(
                    ownedCopies,
                    elapsed,
                    reservoirBatch ? 2 : STABLE_PREFIX_SAMPLES);
        } else if (continuesStablePrefix) {
            // The first proven H still arrived at the same observed cadence;
            // accepting a second H (and then reaching a reservoir boundary)
            // does not invalidate that refill proof. Treat it as one prefix
            // sample instead of restarting learning from scratch.
            state.observeStablePrefix(
                    state.stablePrefixCopies,
                    elapsed,
                    reservoirBatch ? 2 : STABLE_PREFIX_SAMPLES);
        } else if (baselineStatus
                == ProviderTarget.BaselineStatus.COMPLETE
                || baselineStatus
                        == ProviderTarget.BaselineStatus.GROWTH_COMPLETE) {
            state.clearStablePrefix();
        }
        finishBaselineSample(state, gameTick, ownedCopies, true);
        state.reservoirBatch = reservoirBatch;
        state.nextAttemptExploratory = false;
        state.exploratorySuccesses = 0;
        state.exploratoryProbeRejected = false;
        return state.singleChunkRefill
                ? state.singleChunkCoverage
                : state.baselineCoverage;
    }

    private static int beginReservoirCadence(
            State state,
            long gameTick,
            long ownedCopies,
            int acceptedUpperBound) {
        finishBaselineSample(state, gameTick, ownedCopies, true);
        state.reservoirBatch = true;
        state.reservoirCadenceActive = true;
        state.reservoirCadenceCopies = ownedCopies;
        state.reservoirRejectedCoverage = 0;
        state.reservoirAcceptedCoverage = Math.clamp(
                acceptedUpperBound, 1, MAX_COVERAGE_TICKS);
        state.reservoirCycleOwnedCopies = 0L;
        state.reservoirWaterlineSettled = false;
        state.reservoirCycleStartTick = gameTick;
        state.lastReservoirProbeTick = gameTick;
        return scheduleReservoirProbe(state, gameTick);
    }

    /**
     * Discards a stale refill interval after a full reservoir audit succeeds.
     * The new machine state is deliberately relearned from one tick: rejected
     * attempts double the absolute delay from the last refill until a new
     * successful upper bound exists, after which the same window is bisected.
     */
    private static int beginReservoirWindowLearning(
            State state, long gameTick, long ownedCopies) {
        finishBaselineSample(state, gameTick, ownedCopies, true);
        state.reservoirBatch = true;
        state.reservoirCadenceActive = true;
        state.reservoirCadenceCopies = ownedCopies;
        state.reservoirRejectedCoverage = 0;
        state.reservoirAcceptedCoverage = 0;
        state.nextAttemptExploratory = true;
        state.reservoirFullTailAudit = true;
        state.reservoirNeedsFullAudit = true;
        state.reservoirWindowLearning = true;
        state.reservoirAggressiveLearning = true;
        state.reservoirPeriodicAudit = false;
        state.reservoirAuditSuccesses = 0;
        state.reservoirCandidateCoverage = 0;
        state.reservoirCandidateSuccesses = 0;
        state.reservoirCycleOwnedCopies = 0L;
        state.reservoirWaterlineSettled = false;
        state.reservoirCycleStartTick = gameTick;
        state.lastReservoirProbeTick = gameTick;
        state.reservoirWindowStartTick = gameTick;
        return 1;
    }

    private static int recordReservoirSuccess(
            State state,
            long gameTick,
            long ownedCopies,
            boolean exploratoryAttempt) {
        int elapsed = state.reservoirCycleStartTick == Long.MIN_VALUE
                ? 1
                : (int) Math.clamp(
                        gameTick - state.reservoirCycleStartTick,
                        1L,
                        MAX_COVERAGE_TICKS);
        if (state.reservoirCadenceActive
                && state.reservoirCycleOwnedCopies == 0L
                && ownedCopies == state.reservoirCadenceCopies
                && state.lastOwnedCopies > 0L
                && ownedCopies > state.lastOwnedCopies
                && elapsed >= RESERVOIR_WINDOW_TICKS
                && (state.reservoirWindowStartTick == Long.MIN_VALUE
                        || gameTick - state.reservoirWindowStartTick
                                >= RESERVOIR_WINDOW_TICKS)) {
            return beginReservoirWindowLearning(
                    state, gameTick, ownedCopies);
        }
        if (!state.reservoirCadenceActive
                || ownedCopies != state.reservoirCadenceCopies) {
            if (state.reservoirCadenceActive
                    && ownedCopies < state.reservoirCadenceCopies
                    && ownedCopies * 4L
                            >= state.reservoirCadenceCopies * 3L) {
                return beginReservoirWindowLearning(
                        state, gameTick, ownedCopies);
            }
            state.reservoirCadenceActive = true;
            state.reservoirCadenceCopies = ownedCopies;
            state.reservoirRejectedCoverage = 0;
            state.reservoirAcceptedCoverage = elapsed;
        } else {
            if (state.reservoirWindowLearning
                    && (state.reservoirAcceptedCoverage <= 0
                            || elapsed
                                    < state.reservoirAcceptedCoverage)) {
                if (state.reservoirCandidateCoverage == elapsed) {
                    state.reservoirCandidateSuccesses++;
                } else {
                    state.reservoirCandidateCoverage = elapsed;
                    state.reservoirCandidateSuccesses = 1;
                }
                int requiredSamples = state.reservoirAggressiveLearning
                        ? RESERVOIR_RECOVERY_SUCCESS_SAMPLES
                        : RESERVOIR_SUCCESS_SAMPLES;
                if (state.reservoirCandidateSuccesses
                        < requiredSamples) {
                    state.reservoirNeedsFullAudit = true;
                    finishBaselineSample(
                            state, gameTick, ownedCopies, true);
                    finishReservoirCycle(state, gameTick);
                    state.reservoirBatch = true;
                    state.nextAttemptExploratory = true;
                    state.reservoirFullTailAudit = true;
                    state.lastReservoirProbeTick = gameTick;
                    return elapsed;
                }
                state.reservoirCandidateSuccesses = 0;
                if (state.reservoirPeriodicAudit) {
                    state.reservoirAuditSuccesses++;
                }
            }
            state.reservoirAcceptedCoverage = Math.max(
                    1,
                    state.reservoirAcceptedCoverage <= 0
                            ? elapsed
                            : Math.min(
                                    state.reservoirAcceptedCoverage,
                                    elapsed));
        }
        state.reservoirNeedsFullAudit = false;
        finishBaselineSample(state, gameTick, ownedCopies, true);
        finishReservoirCycle(state, gameTick);
        state.reservoirBatch = true;
        return scheduleReservoirProbe(state, gameTick);
    }

    private static void finishReservoirCycle(
            State state, long gameTick) {
        state.reservoirCycleOwnedCopies = 0L;
        state.reservoirCycleStartTick = gameTick;
    }

    private static int recordReservoirBoundary(
            State state, long gameTick, long ownedCopies) {
        int elapsedSinceProgress = state.lastSuccessTick == Long.MIN_VALUE
                ? 1
                : (int) Math.clamp(
                        gameTick - state.lastSuccessTick,
                        1L,
                        MAX_COVERAGE_TICKS);
        state.reservoirCycleOwnedCopies = Math.min(
                state.reservoirCadenceCopies,
                state.reservoirCycleOwnedCopies + ownedCopies);
        state.reservoirWaterlineSettled = true;
        if (state.reservoirCycleOwnedCopies
                >= state.reservoirCadenceCopies) {
            return recordReservoirSuccess(
                    state,
                    gameTick,
                    state.reservoirCadenceCopies,
                    false);
        }
        // Partial ownership is progress, not a rejected timing sample. Keep
        // the full-refill window unchanged and finish this cycle with proven
        // H chunks. Treating this as a new lower bound made a stochastic
        // machine's interval monotonically drift upward after every harmless
        // half refill.
        finishBaselineSample(state, gameTick, ownedCopies, true);
        state.reservoirBatch = true;
        state.nextAttemptExploratory = false;
        state.reservoirFullTailAudit = false;
        long remaining = state.reservoirCadenceCopies
                - state.reservoirCycleOwnedCopies;
        return (int) Math.clamp(
                ceilingDivide(
                        remaining * elapsedSinceProgress,
                        ownedCopies),
                1L,
                MAX_COVERAGE_TICKS);
    }

    private static int recordReservoirFailure(
            State state, long gameTick) {
        if (state.reservoirCycleOwnedCopies > 0L) {
            state.nextAttemptExploratory = false;
            state.reservoirFullTailAudit = false;
            state.exploratorySuccesses = 0;
            state.exploratoryProbeRejected = true;
            state.lastReservoirProbeTick = gameTick;
            return state.lastSuccessTick == Long.MIN_VALUE
                    ? 1
                    : (int) Math.clamp(
                            gameTick - state.lastSuccessTick,
                            1L,
                            MAX_COVERAGE_TICKS);
        }
        int elapsed = state.reservoirCycleStartTick == Long.MIN_VALUE
                ? 1
                : (int) Math.clamp(
                        gameTick - state.reservoirCycleStartTick,
                        1L,
                        MAX_COVERAGE_TICKS);
        state.reservoirRejectedCoverage = Math.max(
                state.reservoirRejectedCoverage, elapsed);
        if (state.reservoirAcceptedCoverage > 0
                && state.reservoirAcceptedCoverage
                <= state.reservoirRejectedCoverage) {
            state.reservoirAcceptedCoverage = Math.min(
                    MAX_COVERAGE_TICKS,
                    state.reservoirRejectedCoverage + 1);
        }
        state.nextAttemptExploratory = state.reservoirWindowLearning;
        state.reservoirFullTailAudit = state.reservoirWindowLearning;
        state.exploratorySuccesses = 0;
        state.exploratoryProbeRejected = true;
        state.lastReservoirProbeTick = gameTick;
        int nextCoverage = nextReservoirCoverage(state);
        return Math.max(1, nextCoverage - elapsed);
    }

    private static int scheduleReservoirProbe(
            State state, long gameTick) {
        state.reservoirFullTailAudit = false;
        int low = state.reservoirRejectedCoverage;
        int high = state.reservoirAcceptedCoverage;
        if (high <= 0) {
            int next = nextReservoirCoverage(state);
            state.nextAttemptExploratory = true;
            state.reservoirFullTailAudit = true;
            state.lastReservoirProbeTick = gameTick;
            return next;
        }
        if (high - low > 1) {
            state.nextAttemptExploratory = true;
            state.reservoirFullTailAudit = true;
            state.lastReservoirProbeTick = gameTick;
            return low + (high - low) / 2;
        }
        if (state.reservoirNeedsFullAudit) {
            state.nextAttemptExploratory = true;
            state.reservoirFullTailAudit = true;
            state.lastReservoirProbeTick = gameTick;
            return high;
        }
        if (high > 1
                && gameTick - state.lastReservoirProbeTick >= 100L) {
            state.reservoirRejectedCoverage = 0;
            state.reservoirWindowLearning = true;
            state.reservoirAggressiveLearning = true;
            state.reservoirPeriodicAudit = true;
            state.reservoirAuditSuccesses = 0;
            state.reservoirWindowStartTick = gameTick;
            state.reservoirCandidateCoverage = 0;
            state.reservoirCandidateSuccesses = 0;
            state.nextAttemptExploratory = true;
            // A periodic speed audit only needs one already proven H. A full
            // H,H,tail transaction would spend three physical pushes merely
            // to ask whether a slow target has become faster.
            state.reservoirFullTailAudit = false;
            state.lastReservoirProbeTick = gameTick;
            return Math.max(1, high / 2);
        }
        state.reservoirWindowLearning = false;
        state.reservoirAggressiveLearning = false;
        state.reservoirPeriodicAudit = false;
        state.reservoirAuditSuccesses = 0;
        state.nextAttemptExploratory = false;
        return high;
    }

    private static int nextReservoirCoverage(State state) {
        int low = state.reservoirRejectedCoverage;
        int high = state.reservoirAcceptedCoverage;
        if (high > low) {
            return low + (high - low) / 2;
        }
        if (low <= 0) {
            return 1;
        }
        return Math.min(MAX_COVERAGE_TICKS, low * 2);
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

    private static int rejectionBackoff(int rejections) {
        int shift = Math.clamp(rejections - 1, 0, 5);
        return Math.min(FILL_FALLBACK_RETRY_TICKS, 1 << shift);
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
        private boolean reservoirBatch;
        private int reservoirRejectedProbes;
        private boolean reservoirProbeSettled;
        private long lastReservoirProbeTick = Long.MIN_VALUE;
        private boolean reservoirCadenceActive;
        private long reservoirCadenceCopies;
        private int reservoirRejectedCoverage;
        private int reservoirAcceptedCoverage = 1;
        private long reservoirCycleOwnedCopies;
        private boolean reservoirWaterlineSettled;
        private long reservoirCycleStartTick = Long.MIN_VALUE;
        private long reservoirWindowStartTick = Long.MIN_VALUE;
        private boolean singleChunkReservoirAudit;
        private boolean singleChunkFullTailAudit;
        private boolean reservoirFullTailAudit;
        private boolean reservoirNeedsFullAudit;
        private boolean reservoirWindowLearning;
        private boolean reservoirAggressiveLearning;
        private boolean reservoirPeriodicAudit;
        private int reservoirAuditSuccesses;
        private int reservoirCandidateCoverage;
        private int reservoirCandidateSuccesses;

        private int recordSingleChunkSuccess(
                boolean exploratoryAttempt, long gameTick) {
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
            if (singleChunkCoverage >= SINGLE_CHUNK_AUDIT_MIN_COVERAGE
                    && (!singleChunkProbeSettled
                            || gameTick - lastReservoirProbeTick >= 100L)) {
                // A settled slow reservoir still needs one bounded audit after
                // its next successful refill. If the machine became faster,
                // the audit reopens the full reservoir transaction on the next
                // tick; if it did not, one rejected audit preserves the proven
                // single-chunk cadence.
                nextAttemptExploratory = true;
                singleChunkReservoirAudit = true;
                singleChunkFullTailAudit = true;
                lastReservoirProbeTick = gameTick;
                return Math.min(10, singleChunkCoverage);
            }
            if (!singleChunkProbeSettled
                    && singleChunkCoverage > 1) {
                nextAttemptExploratory = true;
                singleChunkReservoirAudit = false;
                singleChunkFullTailAudit = false;
                return singleChunkCoverage - 1;
            }
            return singleChunkCoverage;
        }

        private void observeStablePrefix(
                long ownedCopies, long elapsed, int requiredSamples) {
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
            if (stablePrefixSamples >= requiredSamples) {
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
            singleChunkReservoirAudit = false;
            singleChunkFullTailAudit = false;
            reservoirFullTailAudit = false;
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
                reservoirBatch = false;
                reservoirRejectedProbes = 0;
                reservoirProbeSettled = false;
                lastReservoirProbeTick = Long.MIN_VALUE;
                reservoirCadenceActive = false;
                reservoirCadenceCopies = 0L;
                reservoirRejectedCoverage = 0;
                reservoirAcceptedCoverage = 1;
                reservoirCycleOwnedCopies = 0L;
                reservoirWaterlineSettled = false;
                reservoirCycleStartTick = Long.MIN_VALUE;
                reservoirWindowStartTick = Long.MIN_VALUE;
                reservoirFullTailAudit = false;
                reservoirNeedsFullAudit = false;
                reservoirWindowLearning = false;
                reservoirAggressiveLearning = false;
                reservoirPeriodicAudit = false;
                reservoirAuditSuccesses = 0;
                reservoirCandidateCoverage = 0;
                reservoirCandidateSuccesses = 0;
            }
        }
    }
}
