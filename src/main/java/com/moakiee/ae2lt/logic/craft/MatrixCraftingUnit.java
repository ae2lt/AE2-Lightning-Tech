package com.moakiee.ae2lt.logic.craft;

import java.util.Objects;

public record MatrixCraftingUnit(Kind kind, MatrixCoreMode coreMode, int power, int distance) {
    public MatrixCraftingUnit {
        kind = Objects.requireNonNull(kind);
        coreMode = coreMode == null ? MatrixCoreMode.NONE : coreMode;
        power = Math.max(0, power);
        distance = Math.max(0, distance);
    }

    public static MatrixCraftingUnit quantumCore() {
        return new MatrixCraftingUnit(Kind.CORE, MatrixCoreMode.QUANTUM, 0, 0);
    }

    public static MatrixCraftingUnit stableCore() {
        return new MatrixCraftingUnit(Kind.CORE, MatrixCoreMode.STABLE, 0, 0);
    }

    public static MatrixCraftingUnit overloadCore() {
        return new MatrixCraftingUnit(Kind.CORE, MatrixCoreMode.OVERLOAD, 0, 0);
    }

    public static MatrixCraftingUnit creativeCore() {
        return new MatrixCraftingUnit(Kind.CORE, MatrixCoreMode.CREATIVE, 0, 0);
    }

    public static MatrixCraftingUnit t1Threader() {
        return new MatrixCraftingUnit(Kind.THREAD, MatrixCoreMode.NONE, 1, 0);
    }

    public static MatrixCraftingUnit t2Threader() {
        return new MatrixCraftingUnit(Kind.THREAD, MatrixCoreMode.NONE, 2, 0);
    }

    public static MatrixCraftingUnit threadPower(int power) {
        return new MatrixCraftingUnit(Kind.THREAD, MatrixCoreMode.NONE, power, 0);
    }

    public static MatrixCraftingUnit amplifier() {
        return amplifierPower(1);
    }

    public static MatrixCraftingUnit amplifierPower(int power) {
        return new MatrixCraftingUnit(Kind.AMPLIFIER, MatrixCoreMode.NONE, power, 0);
    }

    public static MatrixCraftingUnit t1Cooler(int distance) {
        return coolerPower(1, distance);
    }

    public static MatrixCraftingUnit t2Cooler(int distance) {
        return coolerPower(2, distance);
    }

    public static MatrixCraftingUnit coolerPower(int power, int distance) {
        return new MatrixCraftingUnit(Kind.COOLER, MatrixCoreMode.NONE, power, distance);
    }

    public double adjustedCoolPower() {
        return kind == Kind.COOLER ? power * coolingDecay(distance) : 0.0D;
    }

    /** Stable-core contribution is intentionally separate from thermal thread power. */
    public long stableOperationContribution() {
        if (kind != Kind.THREAD || power <= 0) {
            return 0L;
        }
        return switch (power) {
            case 1 -> MatrixCraftingMath.STABLE_T1_OPERATIONS;
            case 2 -> MatrixCraftingMath.STABLE_T2_OPERATIONS;
            default -> Math.multiplyExact(MatrixCraftingMath.STABLE_T1_OPERATIONS, power);
        };
    }

    public static double coolingDecay(int distance) {
        return switch (distance) {
            case 1 -> 1.0D;
            case 2 -> 0.75D;
            case 3 -> 0.5D;
            case 4 -> 0.25D;
            default -> 0.0D;
        };
    }

    public enum Kind {
        CORE,
        THREAD,
        AMPLIFIER,
        COOLER
    }
}
