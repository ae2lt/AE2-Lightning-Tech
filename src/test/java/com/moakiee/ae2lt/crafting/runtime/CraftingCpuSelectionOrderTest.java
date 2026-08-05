package com.moakiee.ae2lt.crafting.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CraftingCpuSelectionOrderTest {
    @Test
    void prioritizePowerSelectsMoreCoProcessorsAcrossCpuFamilies() {
        assertTrue(compare(false, 8, 4096, false, 2, 1024, true) < 0);
        assertTrue(compare(false, 2, 1024, false, 8, 4096, true) > 0);
    }

    @Test
    void normalOrderingSelectsFewerCoProcessorsAcrossCpuFamilies() {
        assertTrue(compare(false, 2, 4096, false, 8, 1024, false) < 0);
        assertTrue(compare(false, 8, 1024, false, 2, 4096, false) > 0);
    }

    @Test
    void preferredSourceOutranksPowerAndStorage() {
        assertTrue(compare(true, 0, Long.MAX_VALUE, false, Integer.MAX_VALUE, 1, true) < 0);
        assertTrue(compare(true, Integer.MAX_VALUE, Long.MAX_VALUE, false, 0, 1, false) < 0);
    }

    @Test
    void storageBreaksEqualPowerTiesAndExactTiesStayStable() {
        assertTrue(compare(false, 4, 1024, false, 4, 4096, true) < 0);
        assertEquals(0, compare(false, 4, 1024, false, 4, 1024, true));
    }

    private static int compare(
            boolean firstPreferred,
            int firstCoProcessors,
            long firstAvailableStorage,
            boolean secondPreferred,
            int secondCoProcessors,
            long secondAvailableStorage,
            boolean prioritizePower) {
        return CraftingCpuSelectionOrder.compare(
                firstPreferred,
                firstCoProcessors,
                firstAvailableStorage,
                secondPreferred,
                secondCoProcessors,
                secondAvailableStorage,
                prioritizePower);
    }
}
