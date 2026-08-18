package com.moakiee.ae2lt.logic.railgun;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.item.railgun.RailgunChargeTier;
import com.moakiee.ae2lt.item.railgun.RailgunModuleEntries;

/**
 * Per-shot FE and lightning-ammo costs for railgun actions.
 */
public record AmmoCost(long feEnergy, long ehv) {

    public static AmmoCost forCharged(RailgunChargeTier tier, RailgunModuleEntries mods) {
        long ehv = switch (tier) {
            case EHV1 -> AE2LTCommonConfig.railgunEhvCostTier1();
            case EHV2 -> AE2LTCommonConfig.railgunEhvCostTier2();
            case EHV3 -> AE2LTCommonConfig.railgunEhvCostTier3();
            default -> 0L;
        };
        long fe = switch (tier) {
            case EHV1 -> AE2LTCommonConfig.railgunFeCostTier1();
            case EHV2 -> AE2LTCommonConfig.railgunFeCostTier2();
            case EHV3 -> AE2LTCommonConfig.railgunFeCostTier3();
            default -> 0L;
        };
        return new AmmoCost(fe, ehv);
    }

    public static long beamFeCost(RailgunModuleEntries mods) {
        return AE2LTCommonConfig.railgunBeamFeCostPerSettle();
    }

    public static int beamHvCostInterval(RailgunModuleEntries mods) {
        return AE2LTCommonConfig.railgunBeamHvCostInterval();
    }
}
