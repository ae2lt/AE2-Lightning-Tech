package com.moakiee.ae2lt.grid.wirelesslink;

enum NativeHostSafety {
    READY,
    PENDING,
    ALREADY_IN_FREQUENCY,
    CONTROLLER_CONFLICT;

    static NativeHostSafety classify(
            boolean targetNodeReady,
            boolean transmitterReady,
            boolean alreadyInFrequencyGrid,
            boolean controllerConflict) {
        if (!targetNodeReady || !transmitterReady) {
            return PENDING;
        }
        if (alreadyInFrequencyGrid) {
            return ALREADY_IN_FREQUENCY;
        }
        if (controllerConflict) {
            return CONTROLLER_CONFLICT;
        }
        return READY;
    }
}
