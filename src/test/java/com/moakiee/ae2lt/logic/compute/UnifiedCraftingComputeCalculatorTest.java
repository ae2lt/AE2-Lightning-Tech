package com.moakiee.ae2lt.logic.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnifiedCraftingComputeCalculatorTest {
    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;

    @Test
    void peripheralUnitsAlwaysContributeOneRawLogicalUnit() {
        var units = new ComputingUnitTotals(2, 3, 4, 5);

        assertEquals(2, units.dispatchUnits());
        assertEquals(3, units.amplifierUnits());
        assertEquals(4, units.storageUnits());
        assertEquals(5, units.coolingUnits());
        assertEquals(256L, UnifiedCraftingComputeCalculator.rawDispatch(units));
        assertEquals(256L * MIB, UnifiedCraftingComputeCalculator.rawExternalStorage(units));
    }

    @Test
    void baselineCapacityKeepsGrowingPastSixtyFourMib() {
        var oneStorage = UnifiedCraftingComputeCalculator.cpuEnvelope(
                ComputeTier.BASELINE, new ComputingUnitTotals(1, 0, 1, 0));
        var twoStorages = UnifiedCraftingComputeCalculator.cpuEnvelope(
                ComputeTier.BASELINE, new ComputingUnitTotals(1, 0, 2, 0));

        assertEquals(65L * MIB, oneStorage.storageBytes());
        assertEquals(129L * MIB, twoStorages.storageBytes());
    }

    @Test
    void extraDispatchUnitsKeepTheStructureValidAndOnlySetCappedState() {
        var envelope = UnifiedCraftingComputeCalculator.cpuEnvelope(
                ComputeTier.BASELINE, new ComputingUnitTotals(Integer.MAX_VALUE, 0, 0, 0));

        assertEquals(512, envelope.successfulDispatchesPerTick());
        assertEquals(1_024L, envelope.maxCopiesPerTick());
        assertTrue(envelope.dispatchCapped());
    }

    @Test
    void storageArithmeticSaturatesInsteadOfWrapping() {
        assertEquals(Long.MAX_VALUE,
                UnifiedCraftingComputeCalculator.saturatedMultiply(Long.MAX_VALUE, 2L));
        assertEquals(Long.MAX_VALUE,
                UnifiedCraftingComputeCalculator.saturatedAdd(Long.MAX_VALUE - 1L, 2L));
        assertTrue(UnifiedCraftingComputeCalculator.cpuEnvelope(
                ComputeTier.OVERLOAD,
                new ComputingUnitTotals(1, 15, Integer.MAX_VALUE, 0)).storageBytes() > 0L);
    }

    @Test
    void cpuAndMatrixReuseTheSameTierGainsButKeepDifferentRuntimeBudgets() {
        var units = new ComputingUnitTotals(4, 15, 3, 0);
        var cpu = UnifiedCraftingComputeCalculator.cpuEnvelope(ComputeTier.OVERLOAD, units);
        var matrix = UnifiedCraftingComputeCalculator.matrixEnvelope(
                ComputeTier.OVERLOAD, new ComputingUnitTotals(4, 15, 0, 0), 1.0D);

        assertEquals(32L, UnifiedCraftingComputeCalculator.dispatchGain(ComputeTier.OVERLOAD, 15));
        assertEquals(1_024L, UnifiedCraftingComputeCalculator.storageGain(ComputeTier.OVERLOAD, 15));
        assertEquals(256, UnifiedCraftingComputeCalculator.copyGain(ComputeTier.OVERLOAD, 15));
        assertEquals(16_384, cpu.successfulDispatchesPerTick());
        assertEquals(256L * GIB, cpu.storageBytes());
        assertEquals(4_194_304L, matrix.operationsPerTick());
        assertEquals(MatrixComputeEnvelope.MAX_PROVIDER_CALLS_PER_TICK,
                matrix.maxProviderCallsPerTick());
        assertFalse(matrix.unboundedOperations());
    }

    @Test
    void unsupportedUnitTypesAndAmplifierCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> UnifiedCraftingComputeCalculator.cpuEnvelope(
                ComputeTier.QUANTUM, new ComputingUnitTotals(1, 0, 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> UnifiedCraftingComputeCalculator.matrixEnvelope(
                ComputeTier.QUANTUM, new ComputingUnitTotals(1, 0, 1, 0), 1.0D));
        assertThrows(IllegalArgumentException.class, () -> UnifiedCraftingComputeCalculator.cpuEnvelope(
                ComputeTier.OVERLOAD, new ComputingUnitTotals(1, 16, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> UnifiedCraftingComputeCalculator.matrixEnvelope(
                ComputeTier.BASELINE, new ComputingUnitTotals(1, 1, 0, 0), 1.0D));
    }

    @Test
    void multidimensionalKeepsLogicalInfinityButNotProviderCallInfinity() {
        var cpu = UnifiedCraftingComputeCalculator.cpuEnvelope(
                ComputeTier.MULTIDIMENSIONAL, new ComputingUnitTotals(0, 0, 0, 0));
        var matrix = UnifiedCraftingComputeCalculator.matrixEnvelope(
                ComputeTier.MULTIDIMENSIONAL, new ComputingUnitTotals(0, 0, 0, 0), 0.0D);

        assertEquals(Long.MAX_VALUE, cpu.storageBytes());
        assertEquals(Long.MAX_VALUE, cpu.maxCopiesPerTick());
        assertEquals(16_384, cpu.successfulDispatchesPerTick());
        assertEquals(Long.MAX_VALUE, matrix.operationsPerTick());
        assertEquals(16_384, matrix.maxProviderCallsPerTick());
        assertTrue(matrix.unboundedOperations());
    }
}
