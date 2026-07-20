package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class MatrixCraftingProfileTest {
    @Test
    void aggregatesSharedLogicalUnitCounts() {
        var units = List.of(
                MatrixCraftingUnit.quantumCore(),
                MatrixCraftingUnit.t1Threader(),
                MatrixCraftingUnit.t2Threader(),
                MatrixCraftingUnit.t1Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t1Cooler(1),
                MatrixCraftingUnit.t2Cooler(2),
                MatrixCraftingUnit.t1Cooler(4),
                MatrixCraftingUnit.t2Cooler(5));

        var profile = MatrixCraftingProfile.fromUnits(units);

        assertEquals(MatrixCoreMode.QUANTUM, profile.mode());
        assertEquals(1, profile.coreCount());
        assertEquals(2.0D, profile.threadPower(), 0.0001D);
        assertEquals(2.0D, profile.multiPower(), 0.0001D);
        assertEquals(2.0D, profile.coolPower(), 0.0001D);
        assertEquals(2, profile.dispatchUnitCount());
        assertEquals(2, profile.multiplierCount());
        assertEquals(4, profile.coolingUnitCount());
        assertTrue(profile.isValid());
    }

    @Test
    void capsAmplifierCountAtFifteen() {
        var units = new ArrayList<MatrixCraftingUnit>();
        units.add(MatrixCraftingUnit.overloadCore());
        units.add(MatrixCraftingUnit.t1Threader());
        for (int i = 0; i < 16; i++) units.add(MatrixCraftingUnit.t1Multiplier());

        var profile = MatrixCraftingProfile.fromUnits(units);

        assertEquals(16, profile.multiplierCount());
        assertEquals(15.0D, profile.multiPower(), 0.0001D);
        assertTrue(profile.multiplierLimitExceeded());
        assertTrue(profile.hasIssue(MatrixProfileIssue.MULTIPLIER_LIMIT_EXCEEDED));
        assertFalse(profile.isValid());
    }

    @Test
    void aggregateUnitPowerCannotBypassAmplifierLimit() {
        var profile = MatrixCraftingProfile.fromUnits(List.of(
                MatrixCraftingUnit.overloadCore(),
                MatrixCraftingUnit.threadPower(4),
                MatrixCraftingUnit.multiplierPower(20)));

        assertEquals(4.0D, profile.threadPower(), 0.0001D);
        assertEquals(20, profile.multiplierCount());
        assertEquals(15.0D, profile.multiPower(), 0.0001D);
        assertTrue(profile.hasIssue(MatrixProfileIssue.MULTIPLIER_LIMIT_EXCEEDED));
    }

    @Test
    void detectsMissingConflictingAndUnsupportedUnits() {
        var missing = MatrixCraftingProfile.fromUnits(List.of(MatrixCraftingUnit.t1Threader()));
        assertTrue(missing.hasIssue(MatrixProfileIssue.MISSING_CORE));

        var conflict = MatrixCraftingProfile.fromUnits(List.of(
                MatrixCraftingUnit.quantumCore(),
                MatrixCraftingUnit.overloadCore()));
        assertTrue(conflict.hasIssue(MatrixProfileIssue.CONFLICTING_CORES));

        var noDispatch = MatrixCraftingProfile.fromUnits(List.of(MatrixCraftingUnit.quantumCore()));
        assertTrue(noDispatch.hasIssue(MatrixProfileIssue.MISSING_DISPATCH_UNIT));

        var baselineAmplifier = MatrixCraftingProfile.fromUnits(List.of(
                MatrixCraftingUnit.stableCore(),
                MatrixCraftingUnit.t1Threader(),
                MatrixCraftingUnit.t1Multiplier()));
        assertTrue(baselineAmplifier.hasIssue(MatrixProfileIssue.AMPLIFIER_NOT_SUPPORTED));
    }

    @Test
    void overloadSnapshotReachesDocumentedFourMiOperationCeiling() {
        var units = new ArrayList<MatrixCraftingUnit>();
        units.add(MatrixCraftingUnit.overloadCore());
        for (int i = 0; i < 4; i++) units.add(MatrixCraftingUnit.t1Threader());
        for (int i = 0; i < 15; i++) units.add(MatrixCraftingUnit.t1Multiplier());
        var profile = MatrixCraftingProfile.fromUnits(units);

        var snapshot = profile.snapshot(1024);

        assertTrue(snapshot.operationsPerTick() > 4_000_000L);
        assertTrue(snapshot.operationsPerTick() <= 4_194_304L);
    }
}
