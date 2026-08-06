package com.moakiee.ae2lt.celestweave;

/** Pure conversion and cadence rules shared by the optional Mekanism armor integration. */
public final class MekanismProtectionRules {
    public static final int RADIATION_REGEN_INTERVAL_TICKS = 20;
    public static final float MIN_RADIATION_HEALING = 2.0F;
    public static final float MAX_RADIATION_HEALING = 10.0F;

    private MekanismProtectionRules() {
    }

    public static boolean shouldRegenerate(
            long gameTime,
            double radiationLevel,
            double minimumRadiation,
            float health,
            float maximumHealth) {
        return gameTime % RADIATION_REGEN_INTERVAL_TICKS == 0L
                && Double.isFinite(radiationLevel)
                && Double.isFinite(minimumRadiation)
                && radiationLevel >= Math.max(0.0D, minimumRadiation)
                && health > 0.0F
                && health < maximumHealth;
    }

    /**
     * Converts Mekanism's normalized radiation severity to one to five hearts per second.
     */
    public static float radiationHealing(double scaledSeverity) {
        double severity = Double.isFinite(scaledSeverity)
                ? Math.max(0.0D, Math.min(1.0D, scaledSeverity))
                : 0.0D;
        return (float) (MIN_RADIATION_HEALING
                + (MAX_RADIATION_HEALING - MIN_RADIATION_HEALING) * severity);
    }

    public static long absorbedJoules(long availableJoules, double dissipationPercent) {
        if (availableJoules <= 0L || !Double.isFinite(dissipationPercent) || dissipationPercent <= 0.0D) {
            return 0L;
        }
        if (dissipationPercent >= 1.0D) {
            return availableJoules;
        }
        return Math.max(0L, (long) Math.floor(availableJoules * dissipationPercent));
    }

    public static long joulesToForgeEnergy(long joules, double joulesPerForgeEnergy) {
        if (joules <= 0L
                || !Double.isFinite(joulesPerForgeEnergy)
                || joulesPerForgeEnergy <= 0.0D) {
            return 0L;
        }
        double converted = joules / joulesPerForgeEnergy;
        if (converted >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (long) Math.floor(converted));
    }
}
