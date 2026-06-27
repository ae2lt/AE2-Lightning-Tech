package com.moakiee.ae2lt.celestweave.module;

final class FlightAbilityRestoreRules {
    private FlightAbilityRestoreRules() {
    }

    static Target targetForNonGameModePlayer(
            boolean hadMayfly,
            boolean wasFlying,
            boolean capturedGameModeFlight,
            boolean siblingFlightActive) {
        boolean restoreMayfly = hadMayfly && !capturedGameModeFlight;
        boolean restoreFlying = wasFlying && !capturedGameModeFlight;
        boolean targetMayfly = restoreMayfly || siblingFlightActive;
        boolean targetFlying = (restoreFlying || siblingFlightActive) && targetMayfly;
        return new Target(targetMayfly, targetFlying);
    }

    record Target(boolean mayfly, boolean flying) {
    }
}
