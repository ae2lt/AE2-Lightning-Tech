package com.moakiee.ae2lt.logic;

import java.util.HashMap;
import java.util.Map;

import appeng.api.crafting.IPatternDetails;

/** Provider-specific pattern indexing and physical-prefix hints around shared refill timing. */
final class WirelessBatchCadence<T> {
    static final int MAX_COVERAGE_TICKS = TransferCadence.MAX_COVERAGE_TICKS;
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
        boolean rejectedSinceSuccess = state.timing.wasBlocked();
        int delay = state.timing.success(gameTick, ownedCopies, acceptedFullChunk);
        state.nextCapacityAudit = state.timing.isEarlyProbe()
                && !rejectedSinceSuccess
                && gameTick - state.lastCapacityAuditTick >= CAPACITY_AUDIT_INTERVAL;
        return delay;
    }

    int recordFailure(T target, IPatternDetails pattern, long gameTick) {
        var state = state(target, pattern);
        state.expireIfIdle(gameTick);
        state.finishCapacityAudit(gameTick);
        state.nextCapacityAudit = false;
        return state.timing.blocked(gameTick);
    }

    boolean isExploratoryAttempt(T target, IPatternDetails pattern) {
        var state = existingState(target, pattern);
        return state != null && state.timing.isEarlyProbe();
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
        return state.singleChunkRefill || state.timing.isEarlyProbe();
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

    private static final class State {
        private final TransferCadence timing = new TransferCadence();
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
            if (!timing.hasSuccess()) {
                lastCapacityAuditTick = gameTick;
            } else if (nextCapacityAudit) {
                lastCapacityAuditTick = gameTick;
            }
            nextCapacityAudit = false;
        }

        private void expireIfIdle(long gameTick) {
            if (timing.expireIfIdle(gameTick)) {
                lastCapacityAuditTick = Long.MIN_VALUE;
                nextCapacityAudit = false;
                clearStablePrefix();
            }
        }
    }
}
