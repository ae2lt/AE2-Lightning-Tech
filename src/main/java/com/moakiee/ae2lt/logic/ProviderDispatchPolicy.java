package com.moakiee.ae2lt.logic;

/**
 * Pure, allocation-free dispatch decisions for the overloaded pattern provider.
 *
 * <p>This class deliberately knows nothing about worlds, capabilities or provider
 * ownership. Keeping those decisions here makes the batch ramp and overflow retry
 * behavior deterministic and directly testable.
 */
final class ProviderDispatchPolicy {

    private static final int OVERFLOW_RETRY_MIN = 5;
    private static final int OVERFLOW_RETRY_MAX = 20;
    private static final int OVERFLOW_RETRY_STEP = 5;

    enum OverflowAttemptResult {
        CLEARED(true, false, true),
        PROGRESSED(false, true, true),
        BLOCKED(false, true, false);

        private final boolean removeBucket;
        private final boolean reschedule;
        private final boolean persistentStateChanged;

        OverflowAttemptResult(
                boolean removeBucket,
                boolean reschedule,
                boolean persistentStateChanged) {
            this.removeBucket = removeBucket;
            this.reschedule = reschedule;
            this.persistentStateChanged = persistentStateChanged;
        }

        boolean removeBucket() {
            return removeBucket;
        }

        boolean reschedule() {
            return reschedule;
        }

        boolean persistentStateChanged() {
            return persistentStateChanged;
        }
    }

    private ProviderDispatchPolicy() {
    }

    /**
     * Gives the current target an even share of the work that remains for the
     * unvisited targets. Recomputing after every actual acceptance naturally
     * hands rejected or short-accepted work to later targets in the same pass.
     */
    static long evenShare(long remaining, int unvisitedTargets) {
        if (remaining <= 0) {
            return 0;
        }
        if (unvisitedTargets <= 1) {
            return remaining;
        }
        return 1L + (remaining - 1L) / unvisitedTargets;
    }

    /**
     * One provider batch visits each target from its initial ready snapshot at
     * most once. The caller's rotating queue changes the starting target across
     * ticks, while this bound prevents both repeated tail probes and O(N²) scans.
     */
    static int targetVisitBudget(int readyTargets) {
        return Math.max(0, readyTargets);
    }

    /**
     * Per-target, per-call safe ramp: {@code 1, 1, 2, 4, ...}. The next
     * aggregate never exceeds the number of copies fully inserted earlier in
     * the same call, so no historical capacity guess can become stale.
     */
    static long nextRampChunk(long fullCredit, long remaining) {
        if (remaining <= 0L) {
            return 0L;
        }
        long next = fullCredit <= 0L ? 1L : fullCredit;
        return Math.min(next, remaining);
    }

    /**
     * Only a completely inserted chunk earns permission to try a larger chunk.
     * Rejection and defensive overflow both end this target's current ramp.
     */
    static boolean mayContinueRamp(
            long requestedCopies, long ownedCopies, boolean fullyInserted) {
        return requestedCopies > 0L
                && ownedCopies == requestedCopies
                && fullyInserted;
    }

    static long addRampCredit(long fullCredit, long completedCopies) {
        if (fullCredit < 0L || completedCopies <= 0L) {
            throw new IllegalArgumentException("Ramp credit requires non-negative completed work");
        }
        return Long.MAX_VALUE - fullCredit < completedCopies
                ? Long.MAX_VALUE
                : fullCredit + completedCopies;
    }

    /**
     * Aggregated insertion may only be committed when simulation accepts the
     * complete amount. A merely positive result is enough for AE2's one-copy
     * compatibility path, but is not a safe capacity probe for a batch.
     */
    static boolean acceptsCompleteAmount(long requested, long simulated) {
        return requested > 0L && simulated >= requested;
    }

    static int initialOverflowRetryDelay() {
        return OVERFLOW_RETRY_MIN;
    }

    /**
     * Overflow is provider-owned work, not a new dispatch failure. Retry quickly
     * whenever the previous attempt made progress; only consecutive zero-progress
     * attempts back off additively, bounded to 5..20 ticks.
     */
    static int nextOverflowRetryDelay(int currentDelay, OverflowAttemptResult result) {
        if (result == OverflowAttemptResult.PROGRESSED) {
            return OVERFLOW_RETRY_MIN;
        }
        if (result == OverflowAttemptResult.CLEARED) {
            return 0;
        }
        int normalized = Math.clamp(
                currentDelay, OVERFLOW_RETRY_MIN, OVERFLOW_RETRY_MAX);
        return Math.min(OVERFLOW_RETRY_MAX, normalized + OVERFLOW_RETRY_STEP);
    }

}
