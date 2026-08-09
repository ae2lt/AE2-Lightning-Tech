package com.moakiee.ae2lt.logic.railgun;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OverloadExecutionDeathPolicyTest {
    @Test
    void zeroHealthWithoutCommittedDeathStillNeedsFallbackSettlement() {
        assertFalse(OverloadExecutionService.normalDeathCompleted(false, false, false, -1, -1));
    }

    @Test
    void committedRemovalOrPlayerDeathStatCompletesSettlement() {
        assertTrue(OverloadExecutionService.normalDeathCompleted(true, false, false, -1, -1));
        assertTrue(OverloadExecutionService.normalDeathCompleted(false, true, false, -1, -1));
        assertTrue(OverloadExecutionService.normalDeathCompleted(false, false, true, 4, 5));
        assertFalse(OverloadExecutionService.normalDeathCompleted(false, false, true, 5, 5));
    }
}
