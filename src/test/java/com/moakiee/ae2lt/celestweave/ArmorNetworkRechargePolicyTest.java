package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArmorNetworkRechargePolicyTest {
    @Test
    void passiveRechargeAlwaysRequestsTheEntireGapToFull() {
        assertEquals(16_000L, ArmorNetworkRechargePolicy.passiveRechargeRequest(4_000L, 20_000L));
        assertEquals(9_270L, ArmorNetworkRechargePolicy.passiveRechargeRequest(10_730L, 20_000L));
        assertEquals(0L, ArmorNetworkRechargePolicy.passiveRechargeRequest(20_000L, 20_000L));
    }

    @Test
    void successfulPartialFillRetriesImmediatelyUntilAboveHalf() {
        assertFalse(ArmorNetworkRechargePolicy.shouldThrottlePassiveRetry(9_999L, 20_000L, 1_000L));
        assertFalse(ArmorNetworkRechargePolicy.shouldThrottlePassiveRetry(10_000L, 20_000L, 1L));
        assertTrue(ArmorNetworkRechargePolicy.shouldThrottlePassiveRetry(10_001L, 20_000L, 1L));
    }

    @Test
    void emptyNetworkAndAboveHalfBufferUseTwentyTickCooldown() {
        assertTrue(ArmorNetworkRechargePolicy.shouldThrottlePassiveRetry(1_000L, 20_000L, 0L));
        assertEquals(120L, ArmorNetworkRechargePolicy.nextRetryTick(100L));
        assertTrue(ArmorNetworkRechargePolicy.isCoolingDown(120L, 119L));
        assertFalse(ArmorNetworkRechargePolicy.isCoolingDown(120L, 120L));
    }

    @Test
    void retryTickSaturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, ArmorNetworkRechargePolicy.nextRetryTick(Long.MAX_VALUE - 10L));
    }
}
