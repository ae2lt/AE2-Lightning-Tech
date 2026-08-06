package com.moakiee.ae2lt.item.railgun;

public final class RailgunEnergyRules {
    public static final long CHARGE_COST_LV_PER_TICK_FE = 1_000L;
    public static final long CHARGE_COST_MV_PER_TICK_FE = 4_000L;
    public static final long CHARGE_COST_HV_PER_TICK_FE = 10_000L;
    public static final long OVERLOAD_EXECUTION_COST_FE = 20_000_000L;

    private RailgunEnergyRules() {
    }

    public static long chargeCostPerTickFe(RailgunChargeTier tier) {
        return switch (tier) {
            case EHV1 -> CHARGE_COST_LV_PER_TICK_FE;
            case EHV2 -> CHARGE_COST_MV_PER_TICK_FE;
            case EHV3 -> CHARGE_COST_HV_PER_TICK_FE;
            default -> 0L;
        };
    }

    public static long overloadExecutionCostFe() {
        return OVERLOAD_EXECUTION_COST_FE;
    }

    public static int receivableFe(long stored, long capacity, int requested) {
        if (requested <= 0 || capacity <= 0L || stored >= capacity) {
            return 0;
        }
        long room = capacity - Math.max(0L, stored);
        return (int) Math.min(Integer.MAX_VALUE, Math.min(room, requested));
    }
}
