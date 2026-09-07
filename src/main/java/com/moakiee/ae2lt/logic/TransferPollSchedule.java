package com.moakiee.ae2lt.logic;

/**
 * Constant-size polling schedule for an inventory without change callbacks.
 * Keep successful continuous transfers hot; learn a period only after an
 * actual empty/full observation, then check one tick before that period.
 */
public final class TransferPollSchedule {
    private static final int ACTIVE_LEARNING_TICKS = 100;
    private long lastSuccess = Long.MIN_VALUE;
    private int period = 1;
    private int idleDelay = 1;
    private boolean rejected;

    public int success(long tick) {
        long elapsed = lastSuccess == Long.MIN_VALUE ? 0 : tick - lastSuccess;
        period = rejected && elapsed > 0 && elapsed < ACTIVE_LEARNING_TICKS
                ? (int) elapsed : 1;
        lastSuccess = tick;
        rejected = false;
        idleDelay = 1;
        return Math.max(1, period - 1);
    }

    public int failure(long tick, int maximumIdleDelay) {
        rejected = true;
        long elapsed = lastSuccess == Long.MIN_VALUE ? ACTIVE_LEARNING_TICKS : tick - lastSuccess;
        if (elapsed >= 0 && elapsed < ACTIVE_LEARNING_TICKS) {
            return Math.max(1, period - (int) elapsed);
        }
        idleDelay = Math.min(Math.max(1, maximumIdleDelay), idleDelay * 2);
        return idleDelay;
    }

    public void reset() {
        lastSuccess = Long.MIN_VALUE;
        period = idleDelay = 1;
        rejected = false;
    }
}
