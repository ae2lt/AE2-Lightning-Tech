package com.moakiee.ae2lt.logic.craft;

/**
 * Pure limiter math for the matrix crafting multiblock. The formed structure can change later;
 * these formulas only need aggregated chip powers and the current heat value.
 */
public final class MatrixCraftingMath {
    public static final int MATRIX_DELAY_TICKS = 5;
    public static final int CREATIVE_DELAY_TICKS = 1;

    private static final double THREAD_SOFT_CAP = 80.0D;
    private static final double DISPATCH_MIN = 128.0D;
    private static final double DISPATCH_RANGE = 384.0D;
    private static final double BASE_BATCH_MIN = 4.0D;
    private static final double BASE_BATCH_PER_POWER = 0.4D;
    private static final double BATCH_LOAD_LOG_WEIGHT = 0.18D;
    private static final double HEAT_CAPACITY_BASE = 2048.0D;
    private static final double HEAT_CAPACITY_PER_COOL_UNIT = 150.0D;
    private static final double COOLING_BASE = 0.00008D;
    private static final double COOLING_PER_COOL_UNIT = 0.000025D;
    private static final double QUANTUM_HEAT_GAIN = 0.002D;
    private static final double QUANTUM_COLD_FACTOR_FLOOR = 0.45D;
    private static final double QUANTUM_BATCH_FACTOR = 64.0D;
    private static final double OVERLOAD_HEAT_GAIN = 0.0094D;
    private static final double OVERLOAD_FACTOR_RANGE = 1250.0D;

    private MatrixCraftingMath() {
    }

    public static double dispatchBase(double threadPower) {
        double power = nonNegative(threadPower);
        return DISPATCH_MIN + DISPATCH_RANGE * power / (power + THREAD_SOFT_CAP);
    }

    public static double baseBatch(double multiPower) {
        return BASE_BATCH_MIN + BASE_BATCH_PER_POWER * nonNegative(multiPower);
    }

    public static double batchLoad(double batchSize, double baseBatch) {
        double n = nonNegative(batchSize);
        double base = Math.max(baseBatch, 0.000001D);
        return 1.0D + BATCH_LOAD_LOG_WEIGHT * log2(1.0D + n / base);
    }

    public static double dispatches(double dispatchBase, double batchSize, double baseBatch) {
        return nonNegative(dispatchBase) / batchLoad(batchSize, baseBatch);
    }

    public static double coolUnits(double coolPower) {
        return nonNegative(coolPower) / 2.0D;
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
                0.0D,
                Long.MAX_VALUE,
                1.0D,
                Long.MAX_VALUE,
                1.0D);
    }

    public static Snapshot stableSnapshot(double heat, double threadPower, double multiPower, double coolPower) {
        double dispatchBase = dispatchBase(threadPower);
        double baseBatch = baseBatch(multiPower);
        double nextHeat = advanceHeat(heat, dispatchBase * QUANTUM_HEAT_GAIN, coolPower);
        double normalizedHeat = normalizedHeat(nextHeat, coolPower);
        double coldFactor = clamp(1.0D - normalizedHeat, QUANTUM_COLD_FACTOR_FLOOR, 1.0D);
        return snapshot(nextHeat, normalizedHeat, dispatchBase, baseBatch, baseBatch * coldFactor, coldFactor);
    }

    public static Snapshot quantumSnapshot(double heat, double threadPower, double multiPower, double coolPower) {
        double dispatchBase = dispatchBase(threadPower);
        double baseBatch = baseBatch(multiPower);
        double nextHeat = advanceHeat(heat, dispatchBase * QUANTUM_HEAT_GAIN, coolPower);
        double normalizedHeat = normalizedHeat(nextHeat, coolPower);
        double coldFactor = clamp(1.0D - normalizedHeat, QUANTUM_COLD_FACTOR_FLOOR, 1.0D);
        double quantumFactor = QUANTUM_BATCH_FACTOR * coldFactor;
        return snapshot(nextHeat, normalizedHeat, dispatchBase, baseBatch, baseBatch * quantumFactor, quantumFactor);
    }

    public static Snapshot overloadSnapshot(double heat, double threadPower, double multiPower, double coolPower) {
        double dispatchBase = dispatchBase(threadPower);
        double baseBatch = baseBatch(multiPower);
        double nextHeat = advanceHeat(heat, dispatchBase * OVERLOAD_HEAT_GAIN, coolPower);
        double normalizedHeat = normalizedHeat(nextHeat, coolPower);
        double overloadFactor = 1.0D + OVERLOAD_FACTOR_RANGE * overloadHeatCurve(normalizedHeat);
        return snapshot(nextHeat, normalizedHeat, dispatchBase, baseBatch, baseBatch * overloadFactor, overloadFactor);
    }

    private static Snapshot snapshot(double heat,
                                     double normalizedHeat,
                                     double dispatchBase,
                                     double baseBatch,
                                     double batchSize,
                                     double efficiencyFactor) {
        double dispatches = dispatches(dispatchBase, batchSize, baseBatch);
        return new Snapshot(
                heat,
                normalizedHeat,
                dispatchBase,
                baseBatch,
                batchSize,
                dispatches,
                floorToLong(batchSize * dispatches),
                efficiencyFactor);
    }

    private static double advanceHeat(double heat, double heatGain, double coolPower) {
        double coolUnits = coolUnits(coolPower);
        double nextHeat = nonNegative(heat) + nonNegative(heatGain);
        nextHeat -= coolingRate(coolUnits) * nextHeat;
        return nonNegative(nextHeat);
    }

    private static double normalizedHeat(double heat, double coolPower) {
        return clamp01(nonNegative(heat) / heatCapacity(coolUnits(coolPower)));
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0D);
    }

    private static double nonNegative(double value) {
        if (Double.isNaN(value) || value <= 0.0D) return 0.0D;
        return value;
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0D, 1.0D);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static long floorToLong(double value) {
        if (Double.isNaN(value) || value <= 0.0D) return 0L;
        if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return (long) Math.floor(value);
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
