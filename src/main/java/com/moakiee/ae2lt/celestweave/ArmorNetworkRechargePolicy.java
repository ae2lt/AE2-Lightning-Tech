package com.moakiee.ae2lt.celestweave;

public final class ArmorNetworkRechargePolicy {
    public static final int PASSIVE_RECHARGE_INTERVAL_TICKS = 20;

    private ArmorNetworkRechargePolicy() {
    }

    public static boolean shouldPassiveRecharge(long stored, long capacity) {
        return capacity > 0L && room(stored, capacity) > 0L;
    }

    public static long passiveRechargeRequest(long stored, long capacity) {
        return shouldPassiveRecharge(stored, capacity) ? room(stored, capacity) : 0L;
    }

    /**
     * Passive refills keep advancing every tick while a successful transfer leaves the buffer at
     * or below half full. Once above half, or when the network supplied nothing, polling is reduced
     * to once per cooldown interval. Every poll still requests the entire remaining room.
     */
    public static boolean shouldThrottlePassiveRetry(long storedAfter, long capacity, long received) {
        return received <= 0L || isAboveHalf(storedAfter, capacity);
    }

    public static boolean shouldActiveRecharge(long stored, long capacity, long cost) {
        return cost > 0L && capacity > 0L && Math.max(0L, stored) < cost && room(stored, capacity) > 0L;
    }

    public static long activeRechargeRequest(long stored, long capacity, long cost) {
        return shouldActiveRecharge(stored, capacity, cost) ? room(stored, capacity) : 0L;
    }

    public static boolean isCoolingDown(long nextRetryTick, long currentTick) {
        return nextRetryTick > currentTick;
    }

    public static long nextRetryTick(long currentTick) {
        if (currentTick > Long.MAX_VALUE - PASSIVE_RECHARGE_INTERVAL_TICKS) {
            return Long.MAX_VALUE;
        }
        return currentTick + PASSIVE_RECHARGE_INTERVAL_TICKS;
    }

    private static long room(long stored, long capacity) {
        return Math.max(0L, capacity - Math.max(0L, stored));
    }

    private static boolean isAboveHalf(long stored, long capacity) {
        long safeCapacity = Math.max(0L, capacity);
        return Math.max(0L, stored) > safeCapacity / 2L;
    }
}
