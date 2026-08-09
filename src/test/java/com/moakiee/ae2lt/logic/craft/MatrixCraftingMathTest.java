package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MatrixCraftingMathTest {
    @Test
    void baselineComputesOperationsDirectlyWithoutRuntimeDispatchBudget() {
        var snapshot = MatrixCraftingMath.stableSnapshot(0, 4, 0, 0);

        assertEquals(1_024L, snapshot.operationsPerTick());
        assertEquals(1.0D, snapshot.efficiencyFactor(), 0.0001D);
    }

    @Test
    void overloadCurvePeaksAtHalfHeatAndRespectsTierCopyCap() {
        assertEquals(1.0D, MatrixCraftingMath.overloadHeatCurve(0.5D), 0.0001D);
        assertEquals(0.0D, MatrixCraftingMath.overloadHeatCurve(0.0D), 0.0001D);
        assertEquals(0.0D, MatrixCraftingMath.overloadHeatCurve(1.0D), 0.0001D);

        var snapshot = MatrixCraftingMath.overloadSnapshot(1024, 4, 15, 0);

        assertEquals(4_194_304L, snapshot.operationsPerTick());
        assertEquals(1.0D, snapshot.efficiencyFactor(), 0.0001D);
    }

    @Test
    void coolingUsesOneLogicalPointPerPhysicalUnitBeforeDistanceDecay() {
        assertEquals(20.0D, MatrixCraftingMath.coolUnits(20), 0.0001D);
        assertEquals(5048.0D, MatrixCraftingMath.heatCapacity(20), 0.0001D);
        assertEquals(0.00058D, MatrixCraftingMath.coolingRate(20), 0.0000001D);
    }
}
