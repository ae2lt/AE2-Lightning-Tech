package com.moakiee.ae2lt.celestweave;

public final class ArmorPhaseFlightRules {
    private ArmorPhaseFlightRules() {
    }

    public static boolean clientPhaseStateActive(
            boolean serverActive,
            boolean equipped,
            boolean hasCore,
            boolean moduleEnabled,
            boolean configEnabled) {
        return serverActive && configEnabled;
    }

    public static boolean shouldApplyPseudoSpectatorState(
            boolean phaseStateActive,
            boolean afterVanillaNoPhysicsReset) {
        return phaseStateActive && afterVanillaNoPhysicsReset;
    }
}
