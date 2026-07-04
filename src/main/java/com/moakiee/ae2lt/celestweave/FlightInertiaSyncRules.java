package com.moakiee.ae2lt.celestweave;

final class FlightInertiaSyncRules {
    private FlightInertiaSyncRules() {
    }

    static boolean shouldSync(
            boolean changed,
            boolean active,
            boolean forceClientSync,
            boolean flightModule) {
        return flightModule && (changed || active && forceClientSync);
    }

    static boolean targetInertia(
            boolean flightActive,
            boolean flightInertiaEnabled,
            boolean phaseFlightActive,
            boolean phaseInertiaEnabled) {
        if (phaseFlightActive) {
            return phaseInertiaEnabled;
        }
        if (flightActive) {
            return flightInertiaEnabled;
        }
        return true;
    }
}
