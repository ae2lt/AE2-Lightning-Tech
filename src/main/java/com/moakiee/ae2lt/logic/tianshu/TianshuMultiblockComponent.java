package com.moakiee.ae2lt.logic.tianshu;

public enum TianshuMultiblockComponent {
    AIR,
    CASING,
    COOLING,
    GLASS,
    CONTROLLER,
    PORT,
    MAIN_BASELINE,
    MAIN_QUANTUM,
    MAIN_OVERLOAD,
    MAIN_MULTIDIMENSIONAL,
    BLANK_CORE,
    STORAGE_CORE,
    PARALLEL_CORE,
    AMPLIFIER_CORE,
    CLOSED_LOOP_PATTERN_STORAGE,
    CLOSED_LOOP_SEED_STORAGE,
    OTHER;

    /** Blocks accepted anywhere the shell normally uses a phase-change cooling unit. */
    public boolean fillsCoolingPosition() {
        return this == COOLING || isClosedLoopStorage();
    }

    public boolean isClosedLoopStorage() {
        return this == CLOSED_LOOP_PATTERN_STORAGE || this == CLOSED_LOOP_SEED_STORAGE;
    }
}
