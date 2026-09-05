package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.IOSpeedMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ImportScanCache;

class OverloadedInterfaceImportScanCacheTest {
    @Test
    void fastScheduledVisitsCannotReuseAnEarlierEmptyObservation() {
        var cache = new ImportScanCache();
        cache.update(true, 100);
        for (long dueTick = 101; dueTick < 120; dueTick++) {
            assertFalse(cache.canReuseEmpty(dueTick, IOSpeedMode.FAST),
                    "new output must be visible at scheduled tick " + dueTick);
        }
    }

    @Test
    void normalModeKeepsItsEmptyScanTtlAndModeSwitchDoesNotHideOutput() {
        var cache = new ImportScanCache();
        cache.update(true, 100);
        assertTrue(cache.canReuseEmpty(119, IOSpeedMode.NORMAL));
        assertFalse(cache.canReuseEmpty(120, IOSpeedMode.NORMAL));
        assertFalse(cache.canReuseEmpty(101, IOSpeedMode.FAST));
    }

    @Test
    void nonemptyScansNeverSubstituteForReadingChangedKeys() {
        var cache = new ImportScanCache();
        cache.update(true, 100);
        cache.update(false, 101);
        assertFalse(cache.canReuseEmpty(102, IOSpeedMode.NORMAL));
        assertFalse(cache.canReuseEmpty(102, IOSpeedMode.FAST));
    }

    @Test
    void resetAndEarlierWorldTimeInvalidateEmptyObservations() {
        var cache = new ImportScanCache();
        assertFalse(cache.canReuseEmpty(100, IOSpeedMode.NORMAL));
        cache.update(true, 100);
        assertFalse(cache.canReuseEmpty(99, IOSpeedMode.NORMAL));
        cache.clear();
        assertFalse(cache.canReuseEmpty(101, IOSpeedMode.NORMAL));
    }
}
