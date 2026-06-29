package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MatrixCraftingMathTest {
    @Test
    void computesDocumentedThroughputBaseValues() {
        assertEquals(128.0D, MatrixCraftingMath.dispatchBase(0), 0.0001D);
        assertEquals(384.0D, MatrixCraftingMath.dispatchBase(160), 0.0001D);
        assertEquals(12.0D, MatrixCraftingMath.baseBatch(20), 0.0001D);
        assertEquals(1.18D, MatrixCraftingMath.batchLoad(12, 12), 0.0001D);
    }

    @Test
    void stableSnapshotUsesColdFactorAsBatchMultiplier() {
        var snapshot = MatrixCraftingMath.stableSnapshot(0, 160, 20, 0);

        assertEquals(384.0D, snapshot.dispatchBase(), 0.0001D);
        assertEquals(12.0D, snapshot.baseBatch(), 0.0001D);
        assertEquals(0.768D, snapshot.heat(), 0.0001D);
        assertEquals(0.999625D, snapshot.efficiencyFactor(), 0.0001D);
        assertEquals(11.9955D, snapshot.batchSize(), 0.0001D);
        assertEquals(3903L, snapshot.operationsPerTick());
    }

    @Test
    void overloadCurvePeaksAtHalfHeat() {
        assertEquals(1.0D, MatrixCraftingMath.overloadHeatCurve(0.5D), 0.0001D);
        assertEquals(0.0D, MatrixCraftingMath.overloadHeatCurve(0.0D), 0.0001D);
        assertEquals(0.0D, MatrixCraftingMath.overloadHeatCurve(1.0D), 0.0001D);

        var snapshot = MatrixCraftingMath.overloadSnapshot(1024, 160, 20, 0);

        assertEquals(0.501722D, snapshot.normalizedHeat(), 0.0001D);
        assertEquals(1250.9258D, snapshot.efficiencyFactor(), 0.0001D);
        assertEquals(15011.1101D, snapshot.batchSize(), 0.0001D);
        assertEquals(2020998L, snapshot.operationsPerTick());
    }

    @Test
    void coolingUnitsUseHalfOfDistanceAdjustedCoolingPower() {
        assertEquals(10.0D, MatrixCraftingMath.coolUnits(20), 0.0001D);
        assertEquals(3548.0D, MatrixCraftingMath.heatCapacity(10), 0.0001D);
        assertEquals(0.00033D, MatrixCraftingMath.coolingRate(10), 0.0000001D);
    }
}
