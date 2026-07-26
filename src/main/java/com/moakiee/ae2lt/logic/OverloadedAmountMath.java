package com.moakiee.ae2lt.logic;

import java.util.Map;

final class OverloadedAmountMath {
    private OverloadedAmountMath() {
    }

    static long mergeReportedAndSimulatedAmount(long reported, long simulated, long cap) {
        long visible = Math.max(Math.max(0, reported), Math.max(0, simulated));
        return capVisibleAmount(visible, cap);
    }

    static long capVisibleAmount(long available, long cap) {
        if (cap <= 0) return 0;
        long nonNegativeAvailable = Math.max(0, available);
        return cap == Long.MAX_VALUE
                ? nonNegativeAvailable
                : Math.min(nonNegativeAvailable, cap);
    }

    static long saturatingAdd(long a, long b) {
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
    }

    static <K> void mergeSharedExposure(
            Map<K, Long> capByKey,
            Map<K, Long> amountByKey,
            K key,
            long cap,
            long available) {
        capByKey.merge(key, cap, OverloadedAmountMath::saturatingAdd);
        amountByKey.merge(key, Math.max(0, available), Math::max);
    }
}
