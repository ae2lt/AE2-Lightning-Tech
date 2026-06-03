package com.moakiee.ae2lt.logic;

final class OverloadedAmountMath {
    private OverloadedAmountMath() {
    }

    static long mergeReportedAndSimulatedAmount(long reported, long simulated, long cap) {
        long visible = Math.max(Math.max(0, reported), Math.max(0, simulated));
        if (cap <= 0) return 0;
        return cap == Long.MAX_VALUE ? visible : Math.min(visible, cap);
    }
}
