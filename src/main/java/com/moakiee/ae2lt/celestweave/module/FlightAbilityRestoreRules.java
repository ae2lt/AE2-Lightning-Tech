package com.moakiee.ae2lt.celestweave.module;

import com.moakiee.ae2lt.celestweave.PhaseFlightControlRules;

/** Forge 1.20.1 has no shared flight attribute, so Celestweave must restore Abilities.mayfly. */
final class FlightAbilityRestoreRules {
    private FlightAbilityRestoreRules() {
    }

    static Target targetForForgePlayer(
            boolean hadMayfly,
            boolean capturedGameModeFlight,
            boolean currentGameModeFlight,
            boolean currentFlying,
            boolean siblingFlightActive) {
        boolean externalFlightAvailable = hadMayfly && !capturedGameModeFlight;
        boolean targetMayfly = currentGameModeFlight || externalFlightAvailable || siblingFlightActive;
        boolean targetFlying = PhaseFlightControlRules.handoffFlying(currentFlying, targetMayfly);
        return new Target(targetMayfly, targetFlying);
    }

    record Target(boolean mayfly, boolean flying) {
    }
}
