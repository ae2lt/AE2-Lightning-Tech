package com.moakiee.ae2lt.celestweave;

public final class MovementAssistRules {
    public static final double VANILLA_STEP_HEIGHT = 0.6D;

    private MovementAssistRules() {
    }

    public static double movementMultiplier(
            boolean suppressGroundMovement,
            boolean crouching,
            boolean sprinting,
            double walkMultiplier,
            double sprintMultiplier,
            double sneakMultiplier) {
        if (suppressGroundMovement) {
            return 1.0D;
        }
        if (crouching) {
            return positiveOrDefault(sneakMultiplier, 1.0D);
        }
        if (sprinting) {
            return positiveOrDefault(sprintMultiplier, 1.0D);
        }
        return positiveOrDefault(walkMultiplier, 1.0D);
    }

    public static double speedModifierAmount(double multiplier) {
        return positiveOrDefault(multiplier, 1.0D) - 1.0D;
    }

    public static double stepHeightModifierAmount(double configuredHeight) {
        return Math.max(0.0D, positiveOrDefault(configuredHeight, VANILLA_STEP_HEIGHT) - VANILLA_STEP_HEIGHT);
    }

    private static double positiveOrDefault(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }
}
