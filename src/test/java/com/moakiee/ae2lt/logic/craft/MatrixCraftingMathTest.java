package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MatrixCraftingMathTest {
    @Test
    void exposesUnifiedRawUnitAndSquareBatchSemantics() {
        assertEquals(256.0D, MatrixCraftingMath.dispatchBase(2), 0.0001D);
        assertEquals(16.0D, MatrixCraftingMath.baseBatch(15), 0.0001D);
        assertEquals(1.0D, MatrixCraftingMath.batchLoad(65_536, 256), 0.0001D);
    }

    @Test
    void baselineComputesOperationsDirectlyWithoutRuntimeDispatchBudget() {
        var snapshot = MatrixCraftingMath.stableSnapshot(0, 4, 0, 0);

        assertEquals(2.0D, snapshot.baseBatch(), 0.0001D);
        assertEquals(4.0D, snapshot.batchSize(), 0.0001D);
        assertTrue(snapshot.operationsPerTick() > 1_000L);
        assertTrue(snapshot.operationsPerTick() <= 1_024L);
    }

    @Test
    void overloadCurvePeaksAtHalfHeatAndRespectsTierCopyCap() {
        assertEquals(1.0D, MatrixCraftingMath.overloadHeatCurve(0.5D), 0.0001D);
        assertEquals(0.0D, MatrixCraftingMath.overloadHeatCurve(0.0D), 0.0001D);
        assertEquals(0.0D, MatrixCraftingMath.overloadHeatCurve(1.0D), 0.0001D);

        var snapshot = MatrixCraftingMath.overloadSnapshot(1024, 4, 15, 0);

        assertEquals(256.0D, snapshot.baseBatch(), 0.0001D);
        assertEquals(65_536.0D, snapshot.batchSize(), 0.0001D);
        assertTrue(snapshot.operationsPerTick() > 4_000_000L);
        assertTrue(snapshot.operationsPerTick() <= 4_194_304L);
    }

    @Test
    void coolingUsesOneLogicalPointPerPhysicalUnitBeforeDistanceDecay() {
        assertEquals(20.0D, MatrixCraftingMath.coolUnits(20), 0.0001D);
        assertEquals(5048.0D, MatrixCraftingMath.heatCapacity(20), 0.0001D);
        assertEquals(0.00058D, MatrixCraftingMath.coolingRate(20), 0.0000001D);
    }
}
