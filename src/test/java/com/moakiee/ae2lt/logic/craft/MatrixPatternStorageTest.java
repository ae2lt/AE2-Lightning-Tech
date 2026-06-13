package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

class MatrixPatternStorageTest {
    @Test
    void t1AndT2UnitsExposeDocumentedCapacities() {
        assertEquals(36, MatrixPatternStorageUnit.t1().capacity());
        assertEquals(72, MatrixPatternStorageUnit.t2().capacity());
    }

    @Test
    void storageUnitRejectsInsertsAfterItIsFull() {
        var unit = new MatrixPatternStorageUnit(2);
        var first = pattern("first");
        var second = pattern("second");
        var overflow = pattern("overflow");

        assertTrue(unit.insert(first));
        assertTrue(unit.insert(second));
        assertFalse(unit.insert(overflow));

        assertEquals(2, unit.usedSlots());
        assertSame(first, unit.get(0));
        assertSame(second, unit.get(1));
    }

    @Test
    void repositoryFillsUnitsInOrderAndReturnsOverflow() {
        var repository = new MatrixPatternRepository(List.of(
                new MatrixPatternStorageUnit(1),
                new MatrixPatternStorageUnit(2)));
        var first = pattern("first");
        var second = pattern("second");
        var third = pattern("third");
        var overflow = pattern("overflow");

        var rejected = repository.insertAll(List.of(first, second, third, overflow));

        assertEquals(List.of(overflow), rejected);
        assertSame(first, repository.units().get(0).get(0));
        assertSame(second, repository.units().get(1).get(0));
        assertSame(third, repository.units().get(1).get(1));
        assertEquals(0, repository.freeSlots());
    }

    @Test
    void repositoryDeduplicatesAvailablePatternsByIdentity() {
        var shared = pattern("shared");
        var sameNameDifferentPattern = pattern("shared");
        var repository = new MatrixPatternRepository(List.of(
                unit(shared, sameNameDifferentPattern),
                unit(shared, pattern("other"))));

        var patterns = repository.getAvailablePatterns();

        assertEquals(3, patterns.size());
        assertSame(shared, patterns.get(0));
        assertSame(sameNameDifferentPattern, patterns.get(1));
        assertEquals("other", ((FakePattern) patterns.get(2)).id);
    }

    @Test
    void interfaceViewExposesOnlyFirstNonEmptyUnit() {
        var firstPattern = pattern("first");
        var first = unit(firstPattern);
        var secondPattern = pattern("second");
        var second = unit(secondPattern);
        var repository = new MatrixPatternRepository(List.of(first, second));

        assertSame(first, repository.exposedUnit());

        assertSame(firstPattern, first.extract(0));
        assertTrue(first.isEmpty());

        assertSame(second, repository.exposedUnit());
        assertSame(secondPattern, repository.exposedUnit().get(0));
    }

    @Test
    void interfaceViewIsEmptyWhenNoUnitHasPatterns() {
        var repository = new MatrixPatternRepository(List.of(new MatrixPatternStorageUnit(1)));

        assertNull(repository.exposedUnit());
    }

    @Test
    void t1UpgradeToT2PreservesPatternsAndAddsEmptySlots() {
        var unit = MatrixPatternStorageUnit.t1();
        var first = pattern("first");
        var last = pattern("last");
        unit.insert(first);
        for (int i = 1; i < 35; i++) {
            unit.insert(pattern("mid-" + i));
        }
        unit.insert(last);

        var upgraded = unit.upgradeToT2();

        assertEquals(MatrixPatternStorageTier.T2, upgraded.tier());
        assertEquals(72, upgraded.capacity());
        assertSame(first, upgraded.get(0));
        assertSame(last, upgraded.get(35));
        assertNull(upgraded.get(36));
        assertEquals(36, upgraded.usedSlots());
    }

    @Test
    void t2UnitDoesNotUpgradeAgain() {
        var unit = MatrixPatternStorageUnit.t2();
        unit.insert(pattern("kept"));

        assertFalse(unit.canUpgradeToT2());
        assertSame(unit, unit.upgradeToT2());
    }

    @Test
    void repositoryBatchUpgradesFirstT1UnitsOnly() {
        var firstPattern = pattern("first");
        var existingT2Pattern = pattern("existing-t2");
        var secondPattern = pattern("second");
        var thirdPattern = pattern("third");
        var first = MatrixPatternStorageUnit.t1();
        first.insert(firstPattern);
        var existingT2 = MatrixPatternStorageUnit.t2();
        existingT2.insert(existingT2Pattern);
        var second = MatrixPatternStorageUnit.t1();
        second.insert(secondPattern);
        var third = MatrixPatternStorageUnit.t1();
        third.insert(thirdPattern);
        var repository = new MatrixPatternRepository(List.of(first, existingT2, second, third));

        var result = repository.upgradeT1Units(2);

        assertEquals(2, result.upgraded());
        assertEquals(3, result.totalT1BeforeUpgrade());
        assertEquals(1, result.remainingT1());
        assertEquals(MatrixPatternStorageTier.T2, repository.units().get(0).tier());
        assertSame(firstPattern, repository.units().get(0).get(0));
        assertSame(existingT2, repository.units().get(1));
        assertEquals(MatrixPatternStorageTier.T2, repository.units().get(2).tier());
        assertSame(secondPattern, repository.units().get(2).get(0));
        assertEquals(MatrixPatternStorageTier.T1, repository.units().get(3).tier());
        assertSame(thirdPattern, repository.units().get(3).get(0));
    }

    private static MatrixPatternStorageUnit unit(IPatternDetails... patterns) {
        var unit = new MatrixPatternStorageUnit(patterns.length);
        for (var pattern : patterns) {
            unit.insert(pattern);
        }
        return unit;
    }

    private static FakePattern pattern(String id) {
        return new FakePattern(id);
    }

    private static final class FakePattern implements IPatternDetails {
        private final String id;

        private FakePattern(String id) {
            this.id = id;
        }

        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of();
        }
    }
}
