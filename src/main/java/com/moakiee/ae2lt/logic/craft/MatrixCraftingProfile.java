package com.moakiee.ae2lt.logic.craft;

import java.util.List;
import java.util.Set;

public record MatrixCraftingProfile(
        MatrixCoreMode mode,
        int coreCount,
        double threadPower,
        double multiPower,
        double coolPower,
        int multiplierCount,
        boolean multiplierLimitExceeded) {
    public static final int MULTIPLIER_LIMIT = 10;

    public MatrixCraftingProfile {
        mode = mode == null ? MatrixCoreMode.NONE : mode;
        coreCount = Math.max(0, coreCount);
        threadPower = Math.max(0.0D, threadPower);
        multiPower = Math.max(0.0D, multiPower);
        coolPower = Math.max(0.0D, coolPower);
        multiplierCount = Math.max(0, multiplierCount);
    }

    public static MatrixCraftingProfile empty() {
        return new MatrixCraftingProfile(MatrixCoreMode.NONE, 0, 0.0D, 0.0D, 0.0D, 0, false);
    }

    public static MatrixCraftingProfile fromUnits(List<MatrixCraftingUnit> units) {
        if (units == null || units.isEmpty()) return empty();

        MatrixCoreMode mode = MatrixCoreMode.NONE;
        int coreCount = 0;
        double threadPower = 0.0D;
        double multiPower = 0.0D;
        double coolPower = 0.0D;
        int multiplierCount = 0;

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
                case THREAD -> threadPower += unit.power();
                case MULTIPLIER -> {
                    multiplierCount++;
                    if (multiplierCount <= MULTIPLIER_LIMIT) {
                        multiPower += unit.power();
                    }
                }
                case COOLER -> coolPower += unit.adjustedCoolPower();
            }
        }

        boolean multiplierLimitExceeded = multiplierCount > MULTIPLIER_LIMIT;
        if (coreCount == 0) {
            mode = MatrixCoreMode.NONE;
        } else if (coreCount > 1) {
            mode = MatrixCoreMode.CONFLICT;
        }

        return new MatrixCraftingProfile(
                mode,
                coreCount,
                threadPower,
                multiPower,
                coolPower,
                multiplierCount,
                multiplierLimitExceeded);
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
                        && mode != MatrixCoreMode.OVERLOAD)) {
            // has core(s) but not a single valid mode
            issues.add(MatrixProfileIssue.CONFLICTING_CORES);
        }
        if (multiplierLimitExceeded) {
            issues.add(MatrixProfileIssue.MULTIPLIER_LIMIT_EXCEEDED);
        }
        return issues.isEmpty() ? Set.of() : Set.copyOf(issues);
    }

    public MatrixCraftingMath.Snapshot snapshot(double heat) {
        if (!isValid()) {
            return MatrixCraftingMath.idleSnapshot(heat, coolPower);
        }
        return switch (mode) {
            case STABLE -> MatrixCraftingMath.stableSnapshot(heat, threadPower, multiPower, coolPower);
            case OVERLOAD -> MatrixCraftingMath.overloadSnapshot(heat, threadPower, multiPower, coolPower);
            default -> MatrixCraftingMath.quantumSnapshot(heat, threadPower, multiPower, coolPower);
        };
    }
}
