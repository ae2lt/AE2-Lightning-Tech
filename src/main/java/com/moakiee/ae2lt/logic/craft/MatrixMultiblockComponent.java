package com.moakiee.ae2lt.logic.craft;

public enum MatrixMultiblockComponent {
    AIR,
    MATRIX_CASING,
    MATRIX_CONSTRAINT_FRAME,
    MATRIX_GLASS,
    MATRIX_CONTROLLER,
    MATRIX_PORT,
    PATTERN_STORAGE_T1,
    PATTERN_STORAGE_T2,
    STABLE_MAIN_CORE,
    QUANTUM_MAIN_CORE,
    OVERLOAD_MAIN_CORE,
    CREATIVE_MAIN_CORE,
    BLANK_UNIT,
    THREAD_UNIT_T1,
    THREAD_UNIT_T2,
    AMPLIFIER_UNIT,
    THERMAL_CONTROL_UNIT_T1,
    THERMAL_CONTROL_UNIT_T2,
    OTHER;

    public boolean isPatternStorage() {
        return this == PATTERN_STORAGE_T1 || this == PATTERN_STORAGE_T2;
    }

    public boolean isMainCore() {
        return this == STABLE_MAIN_CORE
                || this == QUANTUM_MAIN_CORE
                || this == OVERLOAD_MAIN_CORE
                || this == CREATIVE_MAIN_CORE;
    }

    public boolean isCraftingUnit() {
        return switch (this) {
            case BLANK_UNIT,
                    THREAD_UNIT_T1,
                    THREAD_UNIT_T2,
                    AMPLIFIER_UNIT,
                    THERMAL_CONTROL_UNIT_T1,
                    THERMAL_CONTROL_UNIT_T2 -> true;
            default -> false;
        };
    }

    public boolean isAmplifierUnit() {
        return this == AMPLIFIER_UNIT;
    }

    public MatrixPatternStorageTier patternStorageTier() {
        return switch (this) {
            case PATTERN_STORAGE_T1 -> MatrixPatternStorageTier.T1;
            case PATTERN_STORAGE_T2 -> MatrixPatternStorageTier.T2;
            default -> null;
        };
    }

    public MatrixCraftingUnit toCraftingUnit(int distanceToCore) {
        return switch (this) {
            case STABLE_MAIN_CORE -> MatrixCraftingUnit.stableCore();
            case QUANTUM_MAIN_CORE -> MatrixCraftingUnit.quantumCore();
            case OVERLOAD_MAIN_CORE -> MatrixCraftingUnit.overloadCore();
            case CREATIVE_MAIN_CORE -> MatrixCraftingUnit.creativeCore();
            case THREAD_UNIT_T1 -> MatrixCraftingUnit.t1Threader();
            case THREAD_UNIT_T2 -> MatrixCraftingUnit.t2Threader();
            case AMPLIFIER_UNIT -> MatrixCraftingUnit.amplifier();
            case THERMAL_CONTROL_UNIT_T1 -> MatrixCraftingUnit.t1Cooler(distanceToCore);
            case THERMAL_CONTROL_UNIT_T2 -> MatrixCraftingUnit.t2Cooler(distanceToCore);
            default -> null;
        };
    }
}
