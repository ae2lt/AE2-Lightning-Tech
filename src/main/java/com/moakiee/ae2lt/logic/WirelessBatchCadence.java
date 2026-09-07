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
 * The next visit probes halfway through that interval (one quarter while
 * learning a faster drain). A rejected early probe waits out the remainder. This
 * keeps the state bounded and reacts to both capacity and processing-speed
 * changes without retaining a separate mode for every workload shape.</p>
 *
 * <p>Physical batch safety remains owned by {@link ProviderTarget}; cadence
 * controls only when the target is revisited.</p>
 */
final class WirelessBatchCadence<T> {
    static final int MAX_COVERAGE_TICKS = 100;
    private static final int HISTORY_TTL = 100;
    private static final int CAPACITY_AUDIT_INTERVAL = 50;

    private final Map<T, Map<IPatternDetails, State>> states =
            new HashMap<>();

    int recordSuccess(T target, IPatternDetails pattern, long gameTick,
            long ownedCopies, boolean acceptedFullChunk) {
        return recordSuccess(target, pattern, gameTick, ownedCopies,
                acceptedFullChunk, ProviderTarget.BaselineStatus.NONE);
    }

    int recordSuccess(T target, IPatternDetails pattern, long gameTick,
            long ownedCopies, boolean acceptedFullChunk,
            ProviderTarget.BaselineStatus baselineStatus) {
        if (ownedCopies <= 0L) {
            throw new IllegalArgumentException(
                    "Successful cadence samples must own at least one copy");
        }

        var state = state(target, pattern);
        state.expireIfIdle(gameTick);
        state.finishCapacityAudit(gameTick);
        state.observeBaseline(ownedCopies, baselineStatus);
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
            // A fully accepted caller-limited refill is a censored rate sample:
            // the machine may have consumed more than this visit was allowed to send.
            if (acceptedFullChunk) {
                candidate = Math.min(candidate, (int) Math.min(MAX_COVERAGE_TICKS, elapsed));
            }
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

        boolean rejectedSinceSuccess = state.rejectedElapsed > 0;
        state.lastSuccessTick = gameTick;
        state.lastActivityTick = gameTick;
        state.lastOwnedCopies = ownedCopies;
        state.rejectedElapsed = 0;
        state.nextInterval = probeDelay(
                state.fullInterval, state.rapidSamples > 0);
        state.nextExploratory = state.nextInterval < state.fullInterval;
        state.nextCapacityAudit = state.nextExploratory
                && !rejectedSinceSuccess
                && gameTick - state.lastCapacityAuditTick
                        >= CAPACITY_AUDIT_INTERVAL;
        return state.nextInterval;
    }

    int recordFailure(T target, IPatternDetails pattern, long gameTick) {
        var state = state(target, pattern);
        state.expireIfIdle(gameTick);
        state.finishCapacityAudit(gameTick);
        state.lastActivityTick = gameTick;
        state.fasterCandidate = 0;
        state.nextExploratory = false;
        state.nextCapacityAudit = false;

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
        var state = existingState(target, pattern);
        return state != null && state.nextExploratory;
    }

    boolean shouldReopenReservoirTail(
            T target, IPatternDetails pattern) {
        var state = existingState(target, pattern);
        return state != null && state.nextCapacityAudit;
    }

    boolean usesSingleChunkRefill(T target, IPatternDetails pattern) {
        var state = existingState(target, pattern);
        return state != null && state.singleChunkRefill;
    }

    boolean shouldPreserveBatchHistory(
            T target, IPatternDetails pattern, long gameTick) {
        var state = existingState(target, pattern);
        if (state == null) {
            return false;
        }
        state.expireIfIdle(gameTick);
        return state.singleChunkRefill || state.nextExploratory;
    }

    void removeTarget(T target) {
        states.remove(target);
    }

    void removePattern(T target, IPatternDetails pattern) {
        var byPattern = states.get(target);
        if (byPattern == null) return;
        byPattern.remove(pattern);
        if (byPattern.isEmpty()) states.remove(target);
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

    private static int probeDelay(
            int fullInterval, boolean rapidLearning) {
        if (rapidLearning) {
            return Math.max(1, (fullInterval + 3) / 4);
        }
        return Math.max(1, (fullInterval + 1) / 2);
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
        private boolean nextExploratory;
        private long lastCapacityAuditTick = Long.MIN_VALUE;
        private boolean nextCapacityAudit;
        private long stablePrefixCopies;
        private int stablePrefixSamples;
        private boolean singleChunkRefill;

        private void observeBaseline(
                long ownedCopies,
                ProviderTarget.BaselineStatus baselineStatus) {
            boolean prefix = baselineStatus
                            == ProviderTarget.BaselineStatus.PREFIX_COMPLETE
                    || baselineStatus
                            == ProviderTarget.BaselineStatus.RESERVOIR_PREFIX_COMPLETE;
            if (prefix) {
                if (stablePrefixCopies == ownedCopies) {
                    stablePrefixSamples++;
                } else {
                    stablePrefixCopies = ownedCopies;
                    stablePrefixSamples = 1;
                }
                singleChunkRefill = stablePrefixSamples >= 4;
            } else if (singleChunkRefill
                    && (ownedCopies > stablePrefixCopies
                            || baselineStatus
                                    == ProviderTarget.BaselineStatus.GROWTH_COMPLETE)) {
                clearStablePrefix();
            }
        }

        private void clearStablePrefix() {
            stablePrefixCopies = 0L;
            stablePrefixSamples = 0;
            singleChunkRefill = false;
        }

        private void finishCapacityAudit(long gameTick) {
            if (lastSuccessTick == Long.MIN_VALUE) {
                lastCapacityAuditTick = gameTick;
            } else if (nextCapacityAudit) {
                lastCapacityAuditTick = gameTick;
            }
            nextCapacityAudit = false;
        }

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
                nextExploratory = false;
                lastCapacityAuditTick = Long.MIN_VALUE;
                nextCapacityAudit = false;
                clearStablePrefix();
            }
        }
    }
}
