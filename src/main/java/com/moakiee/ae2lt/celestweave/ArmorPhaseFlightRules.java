package com.moakiee.ae2lt.celestweave;

public final class ArmorPhaseFlightRules {
    private ArmorPhaseFlightRules() {
    }

    public static boolean clientPhaseStateActive(
            boolean serverModuleActive,
            boolean playerFlying,
            boolean serverPhaseModeEnabled) {
        return serverModuleActive && playerFlying && serverPhaseModeEnabled;
    }

    public static boolean shouldApplyPseudoSpectatorState(
            boolean phaseStateActive,
            boolean afterVanillaNoPhysicsReset) {
        return phaseStateActive && afterVanillaNoPhysicsReset;
    }
}
