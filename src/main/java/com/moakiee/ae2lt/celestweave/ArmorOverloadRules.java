package com.moakiee.ae2lt.celestweave;

public final class ArmorOverloadRules {
    public static final long NIGHT_VISION_PASSIVE_DRAIN_FE = 2_000L;
    public static final long WATER_BREATHING_PASSIVE_DRAIN_FE = 2_000L;
    public static final long RESISTANCE_PASSIVE_DRAIN_FE = 1_000L;
    public static final long MATRIX_SHIELD_ACTIVE_COST_FE_PER_DAMAGE = 5_000L;
    public static final long PHASE_SHIELD_ACTIVE_COST_FE_PER_DAMAGE = 20_000L;
    public static final long REFLECT_PASSIVE_DRAIN_FE = 0L;
    public static final long REFLECT_ACTIVE_COST_FE_PER_DAMAGE = 5_000L;
    public static final long DASH_PASSIVE_DRAIN_FE = 0L;
    public static final long DASH_ACTIVE_COST_FE = 50_000L;
    public static final long FLIGHT_HOVER_DRAIN_FE = 5_000L;
    public static final long FLIGHT_MOVING_DRAIN_FE = 10_000L;
    public static final long PURIFICATION_PASSIVE_DRAIN_FE = 6_000L;
    public static final long SATURATION_PASSIVE_DRAIN_FE = 1_200L;
    public static final long DIG_AFFINITY_PASSIVE_DRAIN_FE = 1_800L;
    public static final long REACH_EXTENSION_PASSIVE_DRAIN_FE = 2_500L;
    public static final long PHASE_FLIGHT_PASSIVE_DRAIN_FE = 400_000L;
    public static final long PHASE_FLIGHT_ESCAPE_COST_EHV_PER_TICK = 8L;
    public static final long UNDYING_PASSIVE_DRAIN_FE = 4_000L;
    public static final long UNDYING_TRIGGER_COST_FE = 2_000_000_000L;
    public static final long UNDYING_TRIGGER_COST_EHV = 512L;
    public static final int UNDYING_PULSE_LOAD = 180;

    private ArmorOverloadRules() {
    }

    public static int dynamicCap(ArmorPart part) {
        return part == null ? 0 : part.dynamicCap();
    }
}
