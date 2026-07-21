package com.moakiee.ae2lt.logic.compute;

public final class UnifiedCraftingComputeCalculator {
    public static final long STORAGE_PER_UNIT = 64L << 20;
    public static final int DISPATCH_PER_UNIT = 128;

    private UnifiedCraftingComputeCalculator() {
    }

    public static long rawDispatch(ComputingUnitTotals units) {
        if (units == null) throw new IllegalArgumentException("Units are required");
        return saturatedMultiply(DISPATCH_PER_UNIT, units.dispatchUnits());
    }

    public static long rawExternalStorage(ComputingUnitTotals units) {
        if (units == null) throw new IllegalArgumentException("Units are required");
        return saturatedMultiply(STORAGE_PER_UNIT, units.storageUnits());
    }

    public static CraftingComputeEnvelope cpuEnvelope(ComputeTier tier, ComputingUnitTotals units) {
        validate(tier, units, true);
        if (tier.multidimensional()) {
            return new CraftingComputeEnvelope(
                    Long.MAX_VALUE,
                    tier.dispatchCap(),
                    Long.MAX_VALUE,
                    true,
                    false);
        }

        long dispatchGain = dispatchGain(tier, units.amplifierUnits());
        long rawDispatch = saturatedMultiply(rawDispatch(units), dispatchGain);
        int successfulDispatches = (int) Math.min(rawDispatch, tier.dispatchCap());
        boolean dispatchCapped = rawDispatch >= tier.dispatchCap();

        long externalStorage = rawExternalStorage(units);
        long storage = saturatedAdd(
                tier.internalStorage(),
                saturatedMultiply(externalStorage, storageGain(tier, units.amplifierUnits())));
        int copyGain = copyGain(tier, units.amplifierUnits());
        long copies = Math.min(
                saturatedMultiply(successfulDispatches, copyGain),
                tier.copyCap());
        return new CraftingComputeEnvelope(
                storage, successfulDispatches, copies, false, dispatchCapped);
    }

    public static MatrixComputeEnvelope matrixEnvelope(
            ComputeTier tier,
            ComputingUnitTotals units,
            double thermalEfficiency) {
        validate(tier, units, false);
        if (tier.multidimensional()) {
            return new MatrixComputeEnvelope(
                    Long.MAX_VALUE,
                    MatrixComputeEnvelope.MAX_PROVIDER_CALLS_PER_TICK,
                    1.0D,
                    true);
        }

        long baseOperations = saturatedMultiply(
                saturatedMultiply(
                        rawDispatch(units),
                        dispatchGain(tier, units.amplifierUnits())),
                copyGain(tier, units.amplifierUnits()));
        baseOperations = Math.min(baseOperations, tier.copyCap());
        long operations = floorSaturated(baseOperations * sanitizeEfficiency(thermalEfficiency));
        return new MatrixComputeEnvelope(
                operations,
                MatrixComputeEnvelope.MAX_PROVIDER_CALLS_PER_TICK,
                sanitizeEfficiency(thermalEfficiency),
                false);
    }

    public static long dispatchGain(ComputeTier tier, int amplifierUnits) {
        validateAmplifiers(tier, amplifierUnits);
        if (tier == ComputeTier.BASELINE) return 1L;
        if (tier.multidimensional()) return Long.MAX_VALUE;
        return 2L * (1L + amplifierUnits);
    }

    public static long storageGain(ComputeTier tier, int amplifierUnits) {
        long dispatchGain = dispatchGain(tier, amplifierUnits);
        if (tier == ComputeTier.OVERLOAD) {
            return saturatedMultiply(dispatchGain, dispatchGain);
        }
        return dispatchGain;
    }

    public static int copyGain(ComputeTier tier, int amplifierUnits) {
        validateAmplifiers(tier, amplifierUnits);
        int width = 1 + amplifierUnits;
        return switch (tier) {
            case BASELINE -> 2;
            case QUANTUM -> width;
            case OVERLOAD -> width * width;
            case MULTIDIMENSIONAL -> Integer.MAX_VALUE;
        };
    }

    public static long saturatedAdd(long left, long right) {
        if (left < 0L || right < 0L || left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    public static long saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private static void validate(ComputeTier tier, ComputingUnitTotals units, boolean cpu) {
        if (tier == null || units == null) throw new IllegalArgumentException("Tier and units are required");
        validateAmplifiers(tier, units.amplifierUnits());
        if (!tier.multidimensional() && units.dispatchUnits() <= 0) {
            throw new IllegalArgumentException("A finite compute structure requires at least one dispatch unit");
        }
        if (cpu && units.coolingUnits() > 0) {
            throw new IllegalArgumentException("A crafting CPU cannot use cooling units");
        }
        if (!cpu && units.storageUnits() > 0) {
            throw new IllegalArgumentException("A crafting matrix cannot use storage units");
        }
        if (tier.multidimensional()
                && (units.dispatchUnits() > 0
                        || units.amplifierUnits() > 0
                        || units.storageUnits() > 0
                        || units.coolingUnits() > 0)) {
            throw new IllegalArgumentException("Multidimensional structures use only their main core budget");
        }
    }

    private static void validateAmplifiers(ComputeTier tier, int amplifierUnits) {
        if (tier == null) throw new IllegalArgumentException("Tier is required");
        if (amplifierUnits < 0 || amplifierUnits > tier.maxAmplifierUnits()) {
            throw new IllegalArgumentException("Amplifier unit count is invalid for " + tier);
        }
    }

    private static double sanitizeEfficiency(double efficiency) {
        if (!Double.isFinite(efficiency) || efficiency <= 0.0D) return 0.0D;
        return Math.min(1.0D, efficiency);
    }

    private static long floorSaturated(double value) {
        if (!Double.isFinite(value)) return value > 0.0D ? Long.MAX_VALUE : 0L;
        if (value <= 0.0D) return 0L;
        if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return (long) Math.floor(value);
    }
}
