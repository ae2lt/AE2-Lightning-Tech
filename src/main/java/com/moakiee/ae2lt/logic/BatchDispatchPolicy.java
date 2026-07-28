package com.moakiee.ae2lt.logic;

/**
 * Small, allocation-free policy helpers for overloaded-provider batch dispatch.
 *
 * <p>Targets receive geometrically decreasing shares. Combined with rotating the
 * ready-queue head after every productive target, this gives approximate fairness
 * across calls without scanning every configured machine in one call.
 */
final class BatchDispatchPolicy {

    private static final int MAX_TARGET_ATTEMPTS = 63;

    private BatchDispatchPolicy() {
    }

    /** First target gets about half, then half of what remains, and so on. */
    static long geometricShare(long remaining, int readyTargets) {
        if (remaining <= 0) {
            return 0;
        }
        if (readyTargets <= 1) {
            return remaining;
        }
        return (remaining >>> 1) + (remaining & 1L);
    }

    /**
     * Bound target selection by the amount of useful work, never by the number
     * of configured devices. Rejections therefore cannot turn this into an O(N)
     * scan over a large wireless connection list.
     */
    static int targetAttemptBudget(long copies) {
        if (copies <= 1) {
            return 1;
        }
        return Math.min(MAX_TARGET_ATTEMPTS, 1 + ceilLog2(copies));
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

    private static int ceilLog2(long value) {
        return Long.SIZE - Long.numberOfLeadingZeros(value - 1L);
    }
}
