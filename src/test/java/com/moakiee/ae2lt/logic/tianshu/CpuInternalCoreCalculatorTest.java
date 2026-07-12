package com.moakiee.ae2lt.logic.tianshu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CpuInternalCoreCalculatorTest {
    @Test
    void documentedProfilesProduceExpectedStorageAndParallelism() {
        var baseline = CpuInternalCoreCalculator.calculate(CpuMainCoreTier.BASELINE, 16, 10);
        assertEquals(1024L * 1024L * 1024L, baseline.storageBytes());
        assertEquals(1280, baseline.parallelism());
        assertFalse(baseline.parallelCapped());

        var overload = CpuInternalCoreCalculator.calculate(CpuMainCoreTier.OVERLOAD, 13, 13);
        assertEquals(208L * 1024L * 1024L * 1024L, overload.storageBytes());
        assertEquals(6656, overload.parallelism());
    }

    @Test
    void multidimensionalStorageIsInfiniteAndParallelIsCapped() {
        var profile = CpuInternalCoreCalculator.calculate(CpuMainCoreTier.MULTIDIMENSIONAL, 10, 16);
        assertEquals(Long.MAX_VALUE, profile.storageBytes());
        assertEquals(16_384, profile.parallelism());
        assertFalse(profile.parallelCapped());

        var capped = CpuInternalCoreCalculator.calculate(CpuMainCoreTier.MULTIDIMENSIONAL, 1, 25);
        assertEquals(16_384, capped.parallelism());
        assertTrue(capped.parallelCapped());
    }

    @Test
    void saturatingMultiplyNeverOverflows() {
        assertEquals(Long.MAX_VALUE, CpuInternalCoreCalculator.saturatingMultiply(Long.MAX_VALUE, 2));
    }
}
