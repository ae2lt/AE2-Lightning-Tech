package com.moakiee.ae2lt.logic.railgun;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.item.railgun.RailgunChargeTier;
import com.moakiee.ae2lt.item.railgun.RailgunModules;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Pre-computed per-fire-event damage parameters. The base damage for a tier
 * already factors compute modules and storm bonus; the bypass ratio and chain
 * count are tier-derived.
 */
public record DamageContext(
        double firstDamage,
        double bypassRatio,
        double chainDecay,
        int chainSegments,
        int chainForkCount,
        double chainRadius,
        boolean isMaxCharged,
        boolean isBeam,
        boolean pvpLock) {

    /** Per-segment armor reduction approximation. Uses
     * vanilla rule {@code armor / 25} clamped to 0.8. */
    public static double effectiveArmorReduction(LivingEntity target) {
        return Math.min(0.8D, target.getArmorValue() / 25.0D);
    }

    public static double finalDamage(double base, double bypass, double armorReduction) {
        double effective = armorReduction * (1.0D - bypass);
        return base * (1.0D - effective);
    }

    public static DamageContext buildBeam(Player player, RailgunModules mods, Level level, boolean pvpLock) {
        boolean storm = isStorming(level, player);
        int compute = countChainTuning(mods);
        double base = AE2LTCommonConfig.railgunBeamDamagePerSettle();
        base *= 1.0D + 0.25D * compute;
        if (storm) {
            base *= RailgunDefaults.STORM_DAMAGE_MUL;
        }
        int chains = compute > 0 ? RailgunDefaults.CHAIN_BASE + (compute - 1) * 2 : 0;
        if (storm && chains > 0) {
            chains += RailgunDefaults.STORM_CHAIN_BONUS;
        }
        chains = Math.min(chains, RailgunDefaults.CHAIN_HARD_CAP);
        double radius = RailgunDefaults.CHAIN_RADIUS;
        if (storm) {
            radius += RailgunDefaults.STORM_CHAIN_RADIUS_BONUS;
        }
        return new DamageContext(
                base,
                RailgunDefaults.ARMOR_BYPASS_BEAM,
                RailgunDefaults.CHAIN_DECAY,
                chains,
                compute > 0 ? 1 : 0,
                radius,
                false,
                true,
                pvpLock);
    }

    public static DamageContext buildCharged(Player player, RailgunChargeTier tier, RailgunModules mods, Level level, boolean pvpLock) {
        boolean storm = isStorming(level, player);
        int compute = countChainTuning(mods);
        double base = switch (tier) {
            case EHV1 -> AE2LTCommonConfig.railgunBaseDamageEhv1();
            case EHV2 -> AE2LTCommonConfig.railgunBaseDamageEhv2();
            case EHV3 -> AE2LTCommonConfig.railgunBaseDamageEhv3();
            default -> 0.0D;
        };
        base *= 1.0D + 0.25D * compute;
        if (storm) {
            base *= RailgunDefaults.STORM_DAMAGE_MUL;
        }
        double bypass = switch (tier) {
            case EHV1 -> RailgunDefaults.ARMOR_BYPASS_TIER1;
            case EHV2 -> RailgunDefaults.ARMOR_BYPASS_TIER2;
            case EHV3 -> RailgunDefaults.ARMOR_BYPASS_TIER3;
            default -> 0.0D;
        };
        int chains = compute > 0
                ? switch (tier) {
                    case EHV1 -> 8;
                    case EHV2 -> 14;
                    case EHV3 -> 24;
                    default -> 0;
                } + Math.max(0, compute - 1) * 4
                : 0;
        if (storm) {
            chains += RailgunDefaults.STORM_CHAIN_BONUS;
        }
        chains = Math.min(chains, RailgunDefaults.CHAIN_HARD_CAP);
        // Fork count: per-tier base + compute + storm, capped.
        int forkBase = switch (tier) {
            case EHV1 -> RailgunDefaults.CHAIN_FORK_BASE_EHV1;
            case EHV2 -> RailgunDefaults.CHAIN_FORK_BASE_EHV2;
            case EHV3 -> RailgunDefaults.CHAIN_FORK_BASE_EHV3;
            default -> 1;
        };
        int forks = compute > 0
                ? forkBase + Math.max(0, compute - 1) * RailgunDefaults.CHAIN_FORK_PER_COMPUTE
                : 0;
        if (storm) {
            forks += RailgunDefaults.CHAIN_FORK_STORM_BONUS;
        }
        forks = Math.min(forks, RailgunDefaults.CHAIN_FORK_HARD_CAP);
        double radius = switch (tier) {
            case EHV1 -> 20.0D;
            case EHV2 -> 26.0D;
            case EHV3 -> 32.0D;
            default -> RailgunDefaults.CHAIN_RADIUS;
        };
        if (storm) {
            radius += RailgunDefaults.STORM_CHAIN_RADIUS_BONUS;
        }
        return new DamageContext(
                base,
                bypass,
                RailgunDefaults.CHAIN_DECAY,
                chains,
                forks,
                radius,
                tier.isMax(),
                false,
                pvpLock);
    }

    private static boolean isStorming(Level level, Player player) {
        return level.isThundering() && level.canSeeSky(player.blockPosition());
    }

    private static int countChainTuning(RailgunModules mods) {
        int n = 0;
        for (var cap : mods.capabilities()) {
            if (cap instanceof DeviceCapability.ChainTuning) n++;
        }
        return n;
    }
}
