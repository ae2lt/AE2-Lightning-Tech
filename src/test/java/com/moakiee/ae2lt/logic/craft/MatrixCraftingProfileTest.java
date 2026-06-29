package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MatrixCraftingProfileTest {
    @Test
    void aggregatesChipPowersWithoutLockingBlockNames() {
        var units = List.of(
                MatrixCraftingUnit.quantumCore(),
                MatrixCraftingUnit.t2Threader(),
                MatrixCraftingUnit.threadPower(3),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Cooler(1),
                MatrixCraftingUnit.t2Cooler(2),
                MatrixCraftingUnit.t2Cooler(4),
                MatrixCraftingUnit.t2Cooler(5));

        var profile = MatrixCraftingProfile.fromUnits(units);

        assertEquals(MatrixCoreMode.QUANTUM, profile.mode());
        assertEquals(1, profile.coreCount());
        assertEquals(7.0D, profile.threadPower(), 0.0001D);
        assertEquals(4.0D, profile.multiPower(), 0.0001D);
        assertEquals(4.0D, profile.coolPower(), 0.0001D);
        assertEquals(2, profile.multiplierCount());
        assertTrue(profile.isValid());
    }

    @Test
    void capsMultiplierPowerAndMarksProfileInvalidWhenLimitIsExceeded() {
        var units = List.of(
                MatrixCraftingUnit.overloadCore(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier());

        var profile = MatrixCraftingProfile.fromUnits(units);

        assertEquals(MatrixCoreMode.OVERLOAD, profile.mode());
        assertEquals(11, profile.multiplierCount());
        assertEquals(20.0D, profile.multiPower(), 0.0001D);
        assertTrue(profile.multiplierLimitExceeded());
        assertFalse(profile.isValid());
    }

    @Test
    void detectsMissingAndConflictingCores() {
        var missing = MatrixCraftingProfile.fromUnits(List.of(MatrixCraftingUnit.t2Threader()));
        assertFalse(missing.isValid());
        assertTrue(missing.hasIssue(MatrixProfileIssue.MISSING_CORE));

        var conflict = MatrixCraftingProfile.fromUnits(List.of(
                MatrixCraftingUnit.quantumCore(),
                MatrixCraftingUnit.overloadCore()));

        assertEquals(MatrixCoreMode.CONFLICT, conflict.mode());
        assertEquals(2, conflict.coreCount());
        assertFalse(conflict.isValid());
        assertTrue(conflict.hasIssue(MatrixProfileIssue.CONFLICTING_CORES));
    }

    @Test
    void exposesMultiplierLimitAsProfileIssue() {
        var profile = MatrixCraftingProfile.fromUnits(List.of(
                MatrixCraftingUnit.quantumCore(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier(),
                MatrixCraftingUnit.t2Multiplier()));

        assertFalse(profile.isValid());
        assertTrue(profile.hasIssue(MatrixProfileIssue.MULTIPLIER_LIMIT_EXCEEDED));
    }

    @Test
    void createsRuntimeSnapshotForSelectedCoreMode() {
        var profile = MatrixCraftingProfile.fromUnits(List.of(
                MatrixCraftingUnit.overloadCore(),
                MatrixCraftingUnit.threadPower(160),
                MatrixCraftingUnit.multiplierPower(20)));

        var snapshot = profile.snapshot(1024);

        assertEquals(0.501722D, snapshot.normalizedHeat(), 0.0001D);
        assertEquals(15011.1101D, snapshot.batchSize(), 0.0001D);
        assertEquals(2020998L, snapshot.operationsPerTick());
    }
}
