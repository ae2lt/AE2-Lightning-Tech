package com.moakiee.ae2lt.crafting.timewheel;

/** Debounces the generic AE2 storage warning for a transient deferred requester output. */
final class PendingRequesterOutputWarning {
    static final long WARNING_DELAY_TICKS = 20L;
    private static final long NOT_BLOCKED = Long.MIN_VALUE;

    private long blockedSinceTick = NOT_BLOCKED;

    boolean update(long currentTick, boolean blocked) {
        if (!blocked) {
            reset();
            return false;
        }
        if (blockedSinceTick == NOT_BLOCKED || currentTick < blockedSinceTick) {
            blockedSinceTick = currentTick;
            return false;
        }
        return currentTick - blockedSinceTick >= WARNING_DELAY_TICKS;
    }

    void reset() {
        blockedSinceTick = NOT_BLOCKED;
    }
}
