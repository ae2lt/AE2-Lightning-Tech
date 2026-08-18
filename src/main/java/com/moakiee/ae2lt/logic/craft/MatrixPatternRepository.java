package com.moakiee.ae2lt.logic.craft;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import appeng.api.crafting.IPatternDetails;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

public final class MatrixPatternRepository implements MatrixPatternCore {
    private final List<MatrixPatternStorageUnit> units;

    public MatrixPatternRepository(List<MatrixPatternStorageUnit> units) {
        this.units = new ArrayList<>(units == null ? List.of() : units);
    }

    public List<MatrixPatternStorageUnit> units() {
        return List.copyOf(units);
    }

    public int capacity() {
        int total = 0;
        for (var unit : units) {
            total += unit.capacity();
        }
        return total;
    }

    public int usedSlots() {
        int used = 0;
        for (var unit : units) {
            used += unit.usedSlots();
        }
        return used;
    }

    public int freeSlots() {
        return capacity() - usedSlots();
    }

    public boolean insert(IPatternDetails pattern) {
        if (!isSupportedPattern(pattern)) return false;
        for (var unit : units) {
            if (unit.insert(pattern)) {
                return true;
            }
        }
        return false;
    }

    public List<IPatternDetails> insertAll(List<? extends IPatternDetails> patterns) {
        if (patterns == null || patterns.isEmpty()) return List.of();

        var rejected = new ArrayList<IPatternDetails>();
        for (var pattern : patterns) {
            if (!insert(pattern)) {
                rejected.add(pattern);
            }
        }
        return List.copyOf(rejected);
    }

    public MatrixPatternStorageUnit exposedUnit() {
        for (var unit : units) {
            if (!unit.isEmpty()) {
                return unit;
            }
        }
        return null;
    }

    public UpgradeResult upgradeT1Units(int maxUpgrades) {
        int totalT1 = 0;
        for (var unit : units) {
            if (unit.tier() == MatrixPatternStorageTier.T1) {
                totalT1++;
            }
        }

        int remainingUpgrades = Math.max(0, maxUpgrades);
        int upgraded = 0;
        for (int i = 0; i < units.size() && remainingUpgrades > 0; i++) {
            var unit = units.get(i);
            if (!unit.canUpgradeToT2()) continue;
            units.set(i, unit.upgradeToT2());
            remainingUpgrades--;
            upgraded++;
        }

        return new UpgradeResult(upgraded, totalT1, totalT1 - upgraded);
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        var seen = new IdentityHashMap<IPatternDetails, Boolean>();
        var result = new ArrayList<IPatternDetails>();
        for (var unit : units) {
            for (var pattern : unit.getAvailablePatterns()) {
                if (pattern != null && !seen.containsKey(pattern)) {
                    seen.put(pattern, Boolean.TRUE);
                    result.add(pattern);
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    public boolean hasPattern(IPatternDetails details) {
        if (!isSupportedPattern(details)) return false;
        for (var unit : units) {
            if (unit.hasPattern(details)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSupportedPattern(IPatternDetails pattern) {
        return pattern != null && pattern instanceof IMolecularAssemblerSupportedPattern;
    }

    public record UpgradeResult(int upgraded, int totalT1BeforeUpgrade, int remainingT1) {
    }
}
