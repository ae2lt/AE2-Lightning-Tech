package com.moakiee.ae2lt.logic.craft;

import com.moakiee.ae2lt.logic.compute.ComputeTier;
import com.moakiee.ae2lt.logic.compute.ComputingUnitTotals;
import com.moakiee.ae2lt.logic.compute.UnifiedCraftingComputeCalculator;

/** Pure thermal and logical-operation math for the matrix crafting multiblock. */
public final class MatrixCraftingMath {
    private static final double HEAT_CAPACITY_BASE = 2048.0D;
    private static final double HEAT_CAPACITY_PER_COOL_UNIT = 150.0D;
    private static final double COOLING_BASE = 0.00008D;
    private static final double COOLING_PER_COOL_UNIT = 0.000025D;
    private static final double NORMAL_HEAT_GAIN_PER_DISPATCH_UNIT = 0.256D;
    private static final double OVERLOAD_HEAT_GAIN_PER_DISPATCH_UNIT = 1.2032D;
    private static final double COLD_EFFICIENCY_FLOOR = 0.45D;
    private static final double OVERLOAD_EFFICIENCY_FLOOR = 0.05D;

    private MatrixCraftingMath() {
    }

    /** Raw contribution before main-core gain; retained for status/UI compatibility. */
    public static double dispatchBase(double dispatchUnits) {
        return UnifiedCraftingComputeCalculator.DISPATCH_PER_UNIT * nonNegative(dispatchUnits);
    }

    /** External amplifier width R; retained under the old accessor name for menu compatibility. */
    public static double baseBatch(double amplifierUnits) {
        return 1.0D + nonNegative(amplifierUnits);
    }

    /** A batch with q operation lanes can carry q² logical copies. */
    public static double batchLoad(double copies, double batchOps) {
        double q = Math.max(1.0D, nonNegative(batchOps));
        return nonNegative(copies) / (q * q);
    }

    public static double dispatches(double operationsPerTick, double batchCopies) {
        double copies = nonNegative(batchCopies);
        return copies <= 0.0D ? 0.0D : nonNegative(operationsPerTick) / copies;
    }

    public static double coolUnits(double coolPower) {
        return nonNegative(coolPower);
    }

    public static double heatCapacity(double coolUnits) {
        return HEAT_CAPACITY_BASE + nonNegative(coolUnits) * HEAT_CAPACITY_PER_COOL_UNIT;
    }

    public static double coolingRate(double coolUnits) {
        return COOLING_BASE + nonNegative(coolUnits) * COOLING_PER_COOL_UNIT;
    }

    public static double overloadHeatCurve(double normalizedHeat) {
        double h = clamp01(normalizedHeat);
        return Math.pow(clamp01(4.0D * h * (1.0D - h)), 5.0D);
    }

    public static Snapshot idleSnapshot(double heat, double coolPower) {
        double currentHeat = nonNegative(heat);
        return new Snapshot(
                currentHeat,
                normalizedHeat(currentHeat, coolPower),
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0L,
                0.0D);
    }

    public static Snapshot creativeSnapshot() {
        return new Snapshot(
                0.0D,
                0.0D,
                0.0D,
                Integer.MAX_VALUE,
                Long.MAX_VALUE,
                1.0D,
                Long.MAX_VALUE,
                1.0D);
    }

    public static Snapshot stableSnapshot(
            double heat, double dispatchUnits, double amplifierUnits, double coolPower) {
        return coldSnapshot(ComputeTier.BASELINE, heat, dispatchUnits, amplifierUnits, coolPower);
    }

    public static Snapshot quantumSnapshot(
            double heat, double dispatchUnits, double amplifierUnits, double coolPower) {
        return coldSnapshot(ComputeTier.QUANTUM, heat, dispatchUnits, amplifierUnits, coolPower);
    }

    public static Snapshot overloadSnapshot(
            double heat, double dispatchUnits, double amplifierUnits, double coolPower) {
        double currentHeat = nonNegative(heat);
        double normalizedHeat = normalizedHeat(currentHeat, coolPower);
        double efficiency = OVERLOAD_EFFICIENCY_FLOOR
                + (1.0D - OVERLOAD_EFFICIENCY_FLOOR) * overloadHeatCurve(normalizedHeat);
        return snapshot(
                ComputeTier.OVERLOAD,
                currentHeat,
                normalizedHeat,
                dispatchUnits,
                amplifierUnits,
                coolPower,
                efficiency);
    }

    private static Snapshot coldSnapshot(
            ComputeTier tier,
            double heat,
            double dispatchUnits,
            double amplifierUnits,
            double coolPower) {
        double currentHeat = nonNegative(heat);
        double normalizedHeat = normalizedHeat(currentHeat, coolPower);
        double efficiency = clamp(1.0D - normalizedHeat, COLD_EFFICIENCY_FLOOR, 1.0D);
        return snapshot(
                tier,
                currentHeat,
                normalizedHeat,
                dispatchUnits,
                amplifierUnits,
                coolPower,
                efficiency);
    }

    private static Snapshot snapshot(
            ComputeTier tier,
            double heat,
            double normalizedHeat,
            double dispatchUnits,
            double amplifierUnits,
            double coolPower,
            double efficiency) {
        int dispatchCount = floorToInt(dispatchUnits);
        int amplifierCount = floorToInt(amplifierUnits);
        int coolingCount = floorToInt(coolPower);
        var units = new ComputingUnitTotals(dispatchCount, amplifierCount, 0, coolingCount);
        var envelope = UnifiedCraftingComputeCalculator.matrixEnvelope(tier, units, efficiency);
        int copyGain = UnifiedCraftingComputeCalculator.copyGain(tier, amplifierCount);
        // Legacy menu fields retain the old names until the separate UI redesign. They are
        // diagnostic projections only and do not impose a per-call batch width.
        long batchCopies = UnifiedCraftingComputeCalculator.saturatedMultiply(copyGain, copyGain);
        double rawDispatch = UnifiedCraftingComputeCalculator.saturatedMultiply(
                UnifiedCraftingComputeCalculator.DISPATCH_PER_UNIT * (long) dispatchCount,
                UnifiedCraftingComputeCalculator.dispatchGain(tier, amplifierCount));
        return new Snapshot(
                heat,
                normalizedHeat,
                rawDispatch,
                copyGain,
                batchCopies,
                dispatches(envelope.operationsPerTick(), batchCopies),
                envelope.operationsPerTick(),
                envelope.thermalEfficiency());
    }

    public static double advanceHeatForCompletedTick(
            double heat,
            MatrixCoreMode mode,
            long acceptedOperations,
            long availableOperations,
            double dispatchUnits,
            double coolPower) {
        double load = availableOperations <= 0L
                ? 0.0D
                : clamp((double) Math.max(0L, acceptedOperations) / availableOperations, 0.0D, 1.0D);
        double gainPerUnit = mode == MatrixCoreMode.OVERLOAD
                ? OVERLOAD_HEAT_GAIN_PER_DISPATCH_UNIT
                : NORMAL_HEAT_GAIN_PER_DISPATCH_UNIT;
        double nextHeat = nonNegative(heat) + nonNegative(dispatchUnits) * gainPerUnit * load;
        nextHeat -= coolingRate(coolUnits(coolPower)) * nextHeat;
        return nonNegative(nextHeat);
    }

    private static double normalizedHeat(double heat, double coolPower) {
        return clamp01(nonNegative(heat) / heatCapacity(coolUnits(coolPower)));
    }

    private static int floorToInt(double value) {
        if (!Double.isFinite(value) || value <= 0.0D) return 0;
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.floor(value);
    }

    private static double nonNegative(double value) {
        if (!Double.isFinite(value) || value <= 0.0D) return 0.0D;
        return value;
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0D, 1.0D);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    public record Snapshot(
            double heat,
            double normalizedHeat,
            double dispatchBase,
            double baseBatch,
            double batchSize,
            double dispatches,
            long operationsPerTick,
            double efficiencyFactor) {
    }
}
