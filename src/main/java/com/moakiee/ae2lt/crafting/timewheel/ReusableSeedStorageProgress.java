package com.moakiee.ae2lt.crafting.timewheel;

/** Small arithmetic boundary for returning a reusable seed from a CPU to host storage. */
public final class ReusableSeedStorageProgress {
    public static long transferable(long remainingQuota, long heldInCpu, long writableCapacity) {
        return Math.min(
                Math.max(0L, remainingQuota),
                Math.min(Math.max(0L, heldInCpu), Math.max(0L, writableCapacity)));
    }

    private ReusableSeedStorageProgress() { }
}
