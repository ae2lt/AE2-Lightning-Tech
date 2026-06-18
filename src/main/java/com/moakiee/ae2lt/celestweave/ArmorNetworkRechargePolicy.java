package com.moakiee.ae2lt.celestweave;

public final class ArmorNetworkRechargePolicy {
    public static final int FAILED_RECHARGE_COOLDOWN_TICKS = 20;

    private ArmorNetworkRechargePolicy() {
    }

    public static boolean shouldPassiveRecharge(long stored, long capacity) {
        return capacity > 0L && Math.max(0L, stored) < halfCapacityThreshold(capacity);
    }

    public static long passiveRechargeRequest(long stored, long capacity) {
        return shouldPassiveRecharge(stored, capacity) ? room(stored, capacity) : 0L;
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
        if (currentTick > Long.MAX_VALUE - FAILED_RECHARGE_COOLDOWN_TICKS) {
            return Long.MAX_VALUE;
        }
        return currentTick + FAILED_RECHARGE_COOLDOWN_TICKS;
    }

    private static long room(long stored, long capacity) {
        return Math.max(0L, capacity - Math.max(0L, stored));
    }

    private static long halfCapacityThreshold(long capacity) {
        long safeCapacity = Math.max(0L, capacity);
        return safeCapacity / 2L + (safeCapacity % 2L == 0L ? 0L : 1L);
    }
}
