package com.moakiee.ae2lt.logic.railgun;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.item.railgun.RailgunChargeTier;
import com.moakiee.ae2lt.item.railgun.RailgunModules;

/**
 * Per-shot resource cost for a charged railgun fire. The {@code energy} module
 * (declared as {@link DeviceCapability.EnergyTuning}) trims AE & EHV costs by 50%
 * for charged shots and AE for beam shots; it also widens the beam HV interval.
 */
public record AmmoCost(long aeEnergy, long ehv) {

    public static AmmoCost forCharged(RailgunChargeTier tier, RailgunModules mods) {
        long ae;
        long ehv;
        switch (tier) {
            case EHV1 -> { ae = AE2LTCommonConfig.railgunAeCostTier1(); ehv = AE2LTCommonConfig.railgunEhvCostTier1(); }
            case EHV2 -> { ae = AE2LTCommonConfig.railgunAeCostTier2(); ehv = AE2LTCommonConfig.railgunEhvCostTier2(); }
            case EHV3 -> { ae = AE2LTCommonConfig.railgunAeCostTier3(); ehv = AE2LTCommonConfig.railgunEhvCostTier3(); }
            default -> { ae = 0L; ehv = 0L; }
        }
        double mul = chargedConsumeMul(mods);
        if (mul != 1.0D) {
            ae = (long) Math.ceil(ae * mul);
            ehv = (long) Math.ceil(ehv * mul);
        }
        return new AmmoCost(ae, ehv);
    }

    public static long beamAeCost(RailgunModules mods) {
        long ae = AE2LTCommonConfig.railgunBeamAeCostPerSettle();
        double mul = beamConsumeMul(mods);
        if (mul != 1.0D) {
            ae = (long) Math.ceil(ae * mul);
        }
        return ae;
    }

    public static int beamHvCostInterval(RailgunModules mods) {
        int n = AE2LTCommonConfig.railgunBeamHvCostInterval();
        if (hasEnergyCapability(mods)) {
            n *= 3;
        }
        return Math.max(1, n);
    }

    private static double chargedConsumeMul(RailgunModules mods) {
        // EnergyTuning.consumeMul applies to charged AE+EHV; default 1.0 (no modules).
        for (var cap : mods.capabilities()) {
            if (cap instanceof DeviceCapability.EnergyTuning et) {
                return et.consumeMul();
            }
        }
        return 1.0D;
    }

    private static double beamConsumeMul(RailgunModules mods) {
        // Doc D-energy: beam AE is x0.25 (extra reduction over charged x0.50).
        return hasEnergyCapability(mods) ? 0.25D : 1.0D;
    }

    private static boolean hasEnergyCapability(RailgunModules mods) {
        for (var cap : mods.capabilities()) {
            if (cap instanceof DeviceCapability.EnergyTuning) return true;
        }
        return false;
    }
}
