package com.moakiee.ae2lt.logic.railgun;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OverloadExecutionKillFallbackTest {
    @Test
    void committedDeathSkipsKillEvenWhenEntityCouldStillReportAlive() {
        // Training dummies keep positive health and report a non-dying state, but die()
        // has already committed their death and run dropEquipment(). Calling their
        // overridden kill() here would dismantle them and drop the dummy a second time.
        assertFalse(OverloadExecutionService.needsKillFallback(true, false));
    }

    @Test
    void canceledDeathStillUsesKillFallback() {
        assertTrue(OverloadExecutionService.needsKillFallback(false, false));
    }

    @Test
    void removedEntityNeedsNoKillFallback() {
        assertFalse(OverloadExecutionService.needsKillFallback(false, true));
    }
}
