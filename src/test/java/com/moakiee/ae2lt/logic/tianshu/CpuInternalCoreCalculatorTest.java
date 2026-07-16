package com.moakiee.ae2lt.logic.tianshu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CpuInternalCoreCalculatorTest {
    private static final int BALANCED_CORE_COUNT = 13;
    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;

    @Test
    void balancedProfilesFollowTheTierProgression() {
        var baseline = CpuInternalCoreCalculator.calculate(
                CpuMainCoreTier.BASELINE, BALANCED_CORE_COUNT, BALANCED_CORE_COUNT);
        var quantum = CpuInternalCoreCalculator.calculate(
                CpuMainCoreTier.QUANTUM, BALANCED_CORE_COUNT, BALANCED_CORE_COUNT);
        var overload = CpuInternalCoreCalculator.calculate(
                CpuMainCoreTier.OVERLOAD, BALANCED_CORE_COUNT, BALANCED_CORE_COUNT);
        var multidimensional = CpuInternalCoreCalculator.calculate(
                CpuMainCoreTier.MULTIDIMENSIONAL, BALANCED_CORE_COUNT, BALANCED_CORE_COUNT);

        assertEquals(832L * MIB, baseline.storageBytes());
        assertEquals(1_664, baseline.parallelism());
        assertEquals(13L * GIB, quantum.storageBytes());
        assertEquals(4_992, quantum.parallelism());
        assertEquals(208L * GIB, overload.storageBytes());
        assertEquals(9_984, overload.parallelism());
        assertEquals(Long.MAX_VALUE, multidimensional.storageBytes());
        assertEquals(13_312, multidimensional.parallelism());
    }

    @Test
    void highTierParallelismRespectsTheGlobalCap() {
        var quantum = CpuInternalCoreCalculator.calculate(CpuMainCoreTier.QUANTUM, 1, 25);
        var overload = CpuInternalCoreCalculator.calculate(CpuMainCoreTier.OVERLOAD, 1, 25);
        var multidimensional = CpuInternalCoreCalculator.calculate(CpuMainCoreTier.MULTIDIMENSIONAL, 1, 25);

        assertEquals(9_600, quantum.parallelism());
        assertFalse(quantum.parallelCapped());
        assertEquals(CpuInternalCoreCalculator.PARALLEL_CAP, overload.parallelism());
        assertTrue(overload.parallelCapped());
        assertEquals(CpuInternalCoreCalculator.PARALLEL_CAP, multidimensional.parallelism());
        assertTrue(multidimensional.parallelCapped());
    }
}
