package com.moakiee.ae2lt.crafting.timewheel;

/** Pure reservation arithmetic for the partitioned TimeWheel seed ledgers. */
public final class ReusableSeedReservation {
    /** Aggregate reserve minus the positive balance owned by the requesting task's pool. */
    public static long reservedForTask(
            long totalReserved,
            long ownPoolBalance,
            boolean declaredOwnSeedInput) {
        long total = Math.max(0L, totalReserved);
        if (!declaredOwnSeedInput) return total;
        return Math.max(0L, total - Math.max(0L, ownPoolBalance));
    }

    private ReusableSeedReservation() { }
}
