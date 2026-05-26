package com.moakiee.ae2lt.overload.armor;

public final class ArmorDynamicLoadRules {
    private ArmorDynamicLoadRules() {
    }

    public static int overloadDemand(int currentLoad, int cap, double exponent, double scale) {
        int over = Math.max(0, currentLoad - Math.max(0, cap));
        if (over <= 0 || scale <= 0.0D) {
            return 0;
        }
        double safeExponent = Math.max(1.0D, exponent);
        return (int) Math.ceil(Math.pow(over, safeExponent) * scale);
    }

    public static int flightStateLoad(boolean flying, boolean moving, int hoverLoad, int movingLoad) {
        if (!flying) {
            return 0;
        }
        return moving ? Math.max(0, movingLoad) : Math.max(0, hoverLoad);
    }

    public static int pulseFromAmount(double amount, double loadPerAmount) {
        if (amount <= 0.0D || loadPerAmount <= 0.0D) {
            return 0;
        }
        return (int) Math.ceil(amount * loadPerAmount);
    }

    public static int phaseFlightStateLoad(boolean enabled, boolean insideBlock, int baseLoad, int insideBlockLoad) {
        if (!enabled) {
            return 0;
        }
        int load = Math.max(0, baseLoad);
        if (insideBlock) {
            load += Math.max(0, insideBlockLoad);
        }
        return load;
    }

    public static int cleansePulseLoad(int removedEffects, int loadPerEffect) {
        if (removedEffects <= 0 || loadPerEffect <= 0) {
            return 0;
        }
        return removedEffects * loadPerEffect;
    }

    public static double digSpeedMultiplier(
            boolean underwater,
            boolean airborne,
            double underwaterMultiplier,
            double airborneMultiplier) {
        double multiplier = 1.0D;
        if (underwater) {
            multiplier = Math.max(multiplier, underwaterMultiplier);
        }
        if (airborne) {
            multiplier = Math.max(multiplier, airborneMultiplier);
        }
        return multiplier;
    }
}
