package com.moakiee.ae2lt.celestweave;

final class FlightInertiaSyncRules {
    private FlightInertiaSyncRules() {
    }

    static boolean shouldSync(
            boolean changed,
            boolean active,
            boolean forceClientSync,
            boolean flightModule) {
        return active && flightModule && (changed || forceClientSync);
    }
}
