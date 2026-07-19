package com.moakiee.ae2lt.logic.tianshu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CpuInternalCoreCalculatorTest {
    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;

    @Test
    void baselineUsesIndependentStorageDispatchAndBatchBudgets() {
        var profile = CpuInternalCoreCalculator.calculate(CpuMainCoreTier.BASELINE, 1, 2, 0);

        assertEquals(65L * MIB, profile.storageBytes());
        assertEquals(256, profile.successfulDispatchesPerTick());
        assertEquals(512, profile.maxCopiesPerTick());
        assertEquals(255, profile.coProcessors());
        assertFalse(profile.parallelCapped());
    }

    @Test
    void fullQuantumProfileMatchesDesignTarget() {
        var profile = CpuInternalCoreCalculator.calculate(CpuMainCoreTier.QUANTUM, 4, 1, 15);

        assertEquals(8L * GIB + 256L * MIB, profile.storageBytes());
        assertEquals(3_072, profile.successfulDispatchesPerTick());
        assertEquals(10_240, profile.maxCopiesPerTick());
        assertTrue(profile.parallelCapped());
    }

    @Test
    void fullOverloadUsesOneDimensionalDispatchAndTwoDimensionalStorage() {
        var profile = CpuInternalCoreCalculator.calculate(CpuMainCoreTier.OVERLOAD, 3, 4, 15);

        assertEquals(256L * GIB, profile.storageBytes());
        assertEquals(16_384, profile.successfulDispatchesPerTick());
        assertEquals(4_194_304L, profile.maxCopiesPerTick());
        assertTrue(profile.parallelCapped());
    }

    @Test
    void lowerAmplifierOverloadTradesSlotsForSameMinimumThroughput() {
        var profile = CpuInternalCoreCalculator.calculate(CpuMainCoreTier.OVERLOAD, 9, 8, 7);

        assertEquals(208L * GIB, profile.storageBytes());
        assertEquals(16_384, profile.successfulDispatchesPerTick());
        assertEquals(1_048_576L, profile.maxCopiesPerTick());
        assertTrue(profile.parallelCapped());
    }

    @Test
    void multidimensionalTierRejectsMeaninglessPeripheralComputeUnits() {
        var profile = CpuInternalCoreCalculator.calculate(CpuMainCoreTier.MULTIDIMENSIONAL, 0, 0, 0);

        assertEquals(Long.MAX_VALUE, profile.storageBytes());
        assertEquals(16_384, profile.successfulDispatchesPerTick());
        assertEquals(Long.MAX_VALUE, profile.maxCopiesPerTick());
        assertTrue(profile.unboundedBatch());

        assertThrows(IllegalArgumentException.class,
                () -> CpuInternalCoreCalculator.calculate(CpuMainCoreTier.MULTIDIMENSIONAL, 0, 1, 0));
    }

    @Test
    void amplifierRulesAreTierSpecific() {
        assertThrows(IllegalArgumentException.class,
                () -> CpuInternalCoreCalculator.calculate(CpuMainCoreTier.BASELINE, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> CpuInternalCoreCalculator.calculate(CpuMainCoreTier.QUANTUM, 0, 1, 16));
    }
}
