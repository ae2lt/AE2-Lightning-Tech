package com.moakiee.ae2lt.item.railgun;

/**
 * Discrete charge tier for charged (right-click) railgun fire. The HV value is a
 * sentinel for "no charge yet / left-beam path", not a tier returned from
 * {@link #fromTicks(long)}.
 */
public enum RailgunChargeTier {
    HV,
    EHV1,
    EHV2,
    EHV3;

    public boolean isMax() {
        return this == EHV3;
    }

    public static RailgunChargeTier fromTicks(long charged, int t1Ticks, int t2Ticks, int t3Ticks) {
        if (charged >= t3Ticks) return EHV3;
        if (charged >= t2Ticks) return EHV2;
        if (charged >= t1Ticks) return EHV1;
        return HV;
    }
}
