package com.moakiee.ae2lt.overload.armor;

public final class ArmorOverloadRules {
    public static final long NIGHT_VISION_PASSIVE_DRAIN_FE = 200L;
    public static final long WATER_BREATHING_PASSIVE_DRAIN_FE = 200L;
    public static final long RESISTANCE_PASSIVE_DRAIN_FE = 1_000L;
    public static final long REFLECT_PASSIVE_DRAIN_FE = 600L;
    public static final long DASH_PASSIVE_DRAIN_FE = 100L;
    public static final long DASH_ACTIVE_COST_FE = 500_000L;
    public static final long FLIGHT_HOVER_DRAIN_FE = 5_000L;
    public static final long FLIGHT_MOVING_DRAIN_FE = 20_000L;
    public static final long CLEANSE_PASSIVE_DRAIN_FE = 600L;
    public static final long SATURATION_PASSIVE_DRAIN_FE = 120L;
    public static final long DIG_AFFINITY_PASSIVE_DRAIN_FE = 180L;
    public static final long REACH_EXTENSION_PASSIVE_DRAIN_FE = 250L;
    public static final long PHASE_FLIGHT_PASSIVE_DRAIN_FE = 40_000L;
    public static final long UNDYING_PASSIVE_DRAIN_FE = 2_000L;
    public static final long UNDYING_TRIGGER_COST_FE = 50_000_000L;
    public static final int UNDYING_COMBO_WINDOW_TICKS = 200;
    public static final int UNDYING_PULSE_LOAD = 180;

    private ArmorOverloadRules() {
    }

    public static int dynamicCap(ArmorPart part) {
        return part == null ? 0 : part.dynamicCap();
    }
}
