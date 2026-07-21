package com.moakiee.ae2lt.logic.craft;

import java.util.List;
import java.util.Set;

public record MatrixCraftingProfile(
        MatrixCoreMode mode,
        int coreCount,
        double threadPower,
        double amplifierPower,
        double coolPower,
        int dispatchUnitCount,
        int amplifierUnitCount,
        int coolingUnitCount,
        boolean amplifierLimitExceeded) {
    public static final int AMPLIFIER_LIMIT = 15;

    public MatrixCraftingProfile {
        mode = mode == null ? MatrixCoreMode.NONE : mode;
        coreCount = Math.max(0, coreCount);
        threadPower = Math.max(0.0D, threadPower);
        amplifierPower = Math.max(0.0D, amplifierPower);
        coolPower = Math.max(0.0D, coolPower);
        dispatchUnitCount = Math.max(0, dispatchUnitCount);
        amplifierUnitCount = Math.max(0, amplifierUnitCount);
        coolingUnitCount = Math.max(0, coolingUnitCount);
    }

    public static MatrixCraftingProfile empty() {
        return new MatrixCraftingProfile(
                MatrixCoreMode.NONE, 0, 0.0D, 0.0D, 0.0D, 0, 0, 0, false);
    }

    public static MatrixCraftingProfile fromUnits(List<MatrixCraftingUnit> units) {
        if (units == null || units.isEmpty()) return empty();

        MatrixCoreMode mode = MatrixCoreMode.NONE;
        int coreCount = 0;
        double threadPower = 0.0D;
        double amplifierPower = 0.0D;
        double coolPower = 0.0D;
        int dispatchUnitCount = 0;
        int amplifierUnitCount = 0;
        int coolingUnitCount = 0;

        for (var unit : units) {
            if (unit == null) continue;
            switch (unit.kind()) {
                case CORE -> {
                    coreCount++;
                    if (coreCount == 1) {
                        mode = unit.coreMode();
                    } else {
                        mode = MatrixCoreMode.CONFLICT;
                    }
                }
                case THREAD -> {
                    dispatchUnitCount++;
                    threadPower += unit.power();
                }
                case AMPLIFIER -> {
                    int previousCount = amplifierUnitCount;
                    amplifierUnitCount = saturatedAdd(previousCount, unit.power());
                    int acceptedPower = Math.min(
                            unit.power(),
                            Math.max(0, AMPLIFIER_LIMIT - previousCount));
                    amplifierPower += acceptedPower;
                }
                case COOLER -> {
                    coolingUnitCount++;
                    coolPower += unit.adjustedCoolPower();
                }
            }
        }

        boolean amplifierLimitExceeded = amplifierUnitCount > AMPLIFIER_LIMIT;
        if (coreCount == 0) {
            mode = MatrixCoreMode.NONE;
        } else if (coreCount > 1) {
            mode = MatrixCoreMode.CONFLICT;
        }

        if (coreCount == 1 && mode == MatrixCoreMode.CREATIVE) {
            return new MatrixCraftingProfile(
                    MatrixCoreMode.CREATIVE,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0,
                    0,
                    0,
                    false);
        }

        return new MatrixCraftingProfile(
                mode,
                coreCount,
                threadPower,
                amplifierPower,
                coolPower,
                dispatchUnitCount,
                amplifierUnitCount,
                coolingUnitCount,
                amplifierLimitExceeded);
    }

    public boolean isValid() {
        return issues().isEmpty();
    }

    public boolean hasIssue(MatrixProfileIssue issue) {
        return issues().contains(issue);
    }

    public Set<MatrixProfileIssue> issues() {
        var issues = java.util.EnumSet.noneOf(MatrixProfileIssue.class);
        if (coreCount == 0) {
            issues.add(MatrixProfileIssue.MISSING_CORE);
        } else if (coreCount > 1
                || mode == MatrixCoreMode.CONFLICT
                || (mode != MatrixCoreMode.STABLE
                        && mode != MatrixCoreMode.QUANTUM
                        && mode != MatrixCoreMode.OVERLOAD
                        && mode != MatrixCoreMode.CREATIVE)) {
            // has core(s) but not a single valid mode
            issues.add(MatrixProfileIssue.CONFLICTING_CORES);
        }
        if (amplifierLimitExceeded) {
            issues.add(MatrixProfileIssue.AMPLIFIER_LIMIT_EXCEEDED);
        }
        if (coreCount == 1 && (mode == MatrixCoreMode.STABLE || mode == MatrixCoreMode.CREATIVE)
                && amplifierUnitCount > 0) {
            issues.add(MatrixProfileIssue.AMPLIFIER_NOT_SUPPORTED);
        }
        if (coreCount == 1 && mode != MatrixCoreMode.CREATIVE && threadPower <= 0.0D) {
            issues.add(MatrixProfileIssue.MISSING_DISPATCH_UNIT);
        }
        return issues.isEmpty() ? Set.of() : Set.copyOf(issues);
    }

    public MatrixCraftingMath.Snapshot snapshot(double heat) {
        if (!isValid()) {
            return MatrixCraftingMath.idleSnapshot(heat, coolPower);
        }
        return switch (mode) {
            case STABLE -> MatrixCraftingMath.stableSnapshot(heat, threadPower, amplifierPower, coolPower);
            case OVERLOAD -> MatrixCraftingMath.overloadSnapshot(heat, threadPower, amplifierPower, coolPower);
            case CREATIVE -> MatrixCraftingMath.creativeSnapshot();
            default -> MatrixCraftingMath.quantumSnapshot(heat, threadPower, amplifierPower, coolPower);
        };
    }

    private static int saturatedAdd(int left, int right) {
        long result = (long) left + right;
        return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }
}
