package com.moakiee.ae2lt.celestweave;

public final class ArmorMitigationRules {
    public enum DamageClass {
        ENVIRONMENT,
        ORDINARY,
        HARD
    }

    private static final float MID_ORDINARY_PASS_RATE = 0.2F;
    private static final float MID_HARD_PASS_RATE = 0.5F;

    private ArmorMitigationRules() {
    }

    public static float apply(String stage, DamageClass damageClass, float incomingDamage) {
        float incoming = Math.max(0.0F, incomingDamage);
        if (incoming <= 0.0F) {
            return 0.0F;
        }
        return switch (stage) {
            case "matrix_shield" -> applyMidStage(damageClass, incoming);
            case "phase_shield" -> 0.0F;
            default -> incoming;
        };
    }

    private static float applyMidStage(DamageClass damageClass, float incoming) {
        return switch (damageClass) {
            case ENVIRONMENT -> 0.0F;
            case HARD -> incoming * MID_HARD_PASS_RATE;
            case ORDINARY -> incoming * MID_ORDINARY_PASS_RATE;
        };
    }
}
