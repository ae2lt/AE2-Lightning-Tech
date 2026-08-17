package com.moakiee.ae2lt.celestweave.module;

import com.moakiee.ae2lt.celestweave.PhaseFlightControlRules;

/** Resolves the result of Forge's one-tick external flight permission probe. */
final class FlightAbilityRestoreRules {
    private FlightAbilityRestoreRules() {
    }

    static Target targetAfterReleaseProbe(
            boolean currentGameModeFlight,
            boolean mayflyReasserted,
            boolean currentFlying) {
        boolean targetMayfly = currentGameModeFlight || mayflyReasserted;
        boolean targetFlying = PhaseFlightControlRules.handoffFlying(currentFlying, targetMayfly);
        return new Target(targetMayfly, targetFlying);
    }

    record Target(boolean mayfly, boolean flying) {
    }
}
