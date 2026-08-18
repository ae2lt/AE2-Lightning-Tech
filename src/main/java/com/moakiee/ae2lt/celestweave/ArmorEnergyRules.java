package com.moakiee.ae2lt.celestweave;

public final class ArmorEnergyRules {
    public static final long BASE_CAPACITY_FE = 10_000_000L;
    public static final long MODULE_T1_CAPACITY_FE = 1_000_000_000L;
    public static final long MODULE_T2_CAPACITY_FE = 5_000_000_000L;
    public static final long MODULE_T3_CAPACITY_FE = 20_000_000_000L;
    public static final long MODULE_T1_LEGACY_CAPACITY_FE = 100_000_000L;
    public static final long MODULE_T2_LEGACY_CAPACITY_FE = 500_000_000L;
    public static final long MODULE_T3_LEGACY_CAPACITY_FE = 2_000_000_000L;

    private ArmorEnergyRules() {
    }

    public static long capacityForExtraModuleFe(long extraModuleFe) {
        long module = Math.max(0L, extraModuleFe);
        return module > 0L ? module : BASE_CAPACITY_FE;
    }

    public static int receivableFe(long stored, long capacity, int requested) {
        if (requested <= 0 || capacity <= 0L || stored >= capacity) {
            return 0;
        }
        long room = capacity - Math.max(0L, stored);
        return (int) Math.min(Integer.MAX_VALUE, Math.min(room, requested));
    }
}
