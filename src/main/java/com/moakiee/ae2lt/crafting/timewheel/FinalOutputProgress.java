package com.moakiee.ae2lt.crafting.timewheel;

/** Final-output accounting shared by the time-wheel CPU and its regression tests. */
final class FinalOutputProgress {
    private FinalOutputProgress() {
    }

    static long completedAmount(boolean standalone, long offered, long requesterAccepted) {
        long boundedOffered = Math.max(0L, offered);
        if (standalone) {
            return boundedOffered;
        }
        return Math.min(boundedOffered, Math.max(0L, requesterAccepted));
    }

    /**
     * Amount a requester job must retain in the CPU and retry later. Standalone jobs deliberately
     * leave their unaccepted output in the current ME insertion chain instead.
     */
    static long deferredRequesterAmount(boolean standalone, long offered, long requesterAccepted) {
        if (standalone) {
            return 0L;
        }
        long boundedOffered = Math.max(0L, offered);
        long boundedAccepted = Math.min(boundedOffered, Math.max(0L, requesterAccepted));
        return boundedOffered - boundedAccepted;
    }

    /**
     * Final output that can be recovered from a terminal CPU inventory without consuming reusable
     * seeds or output that is still explicitly retained for loop acceleration.
     */
    static long recoverableInventoryAmount(
            long held, long reusableReserve, long retained, long remainingDemand) {
        long protectedAmount = addSaturated(
                Math.max(0L, reusableReserve), Math.max(0L, retained));
        long unprotected = Math.max(0L, Math.max(0L, held) - protectedAmount);
        return Math.min(unprotected, Math.max(0L, remainingDemand));
    }

    private static long addSaturated(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
