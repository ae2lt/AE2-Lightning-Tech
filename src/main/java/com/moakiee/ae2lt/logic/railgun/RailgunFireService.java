package com.moakiee.ae2lt.logic.railgun;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.energy.LightningCompensationPolicy;
import com.moakiee.ae2lt.item.railgun.RailgunChargeTier;
import com.moakiee.ae2lt.item.railgun.RailgunModuleEntries;
import com.moakiee.ae2lt.item.railgun.RailgunStructuralCore;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.network.NetworkHandler;
import com.moakiee.ae2lt.network.railgun.RailgunFirePacket;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModDamageTypes;
import com.moakiee.ae2lt.registry.ModMobEffects;

/**
 * Central charged-fire service. Encapsulates the right-click charged fire path:
 * 1. Resolve bound grid; abort with HUD message if invalid.
 * 2. Deduct FE via the per-stack {@link RailgunEnergyBuffer} and extract EHV/HV
 *    ammunition from the network's storage. FE is refunded if EHV/HV fails.
 * 3. Raycast first target; build damage context; fan out chain + penetration +
 *    pulse hits.
 * 4. Apply damage to each target (ELECTROMAGNETIC damage type, manually
 *    armor-bypassed).
 * 5. Schedule terrain destruction job (if user has it enabled).
 * 6. Fire recoil + broadcast client effects.
 */
public final class RailgunFireService {

    private RailgunFireService() {}

    public static RailgunChargeTier tierForCharge(long ticks, RailgunModuleEntries mods) {
        // Tier thresholds are fixed in "charge units" — ACCEL modules no longer
        // shrink the thresholds. Instead, they speed up accumulation in the
        // item's onUseTick (+N charge units per real tick). This way the thresholds
        // stay readable and the HUD progress maps 1:1 to the real charge counter.
        return RailgunChargeTier.fromTicks(
                ticks,
                RailgunDefaults.CHARGE_TICKS_TIER1,
                RailgunDefaults.CHARGE_TICKS_TIER2,
                RailgunDefaults.CHARGE_TICKS_TIER3);
    }

    /** Number of ACCEL modules installed; exposed for {@code onUseTick} step-up. */
    public static int countAccelerationModules(RailgunModuleEntries mods) {
        return countAccelerationFactor(mods);
    }

    public static void fireCharged(ServerLevel level, ServerPlayer player, ItemStack stack, RailgunChargeTier tier) {
        fireCharged(level, player, stack, tier, -1L);
    }

    public static void fireCharged(ServerLevel level, ServerPlayer player, ItemStack stack, RailgunChargeTier tier, long chargedTicks) {
        if (tier == RailgunChargeTier.HV) return;
        if (!RailgunStructuralCore.hasCore(stack)) {
            sendFail(player, "ae2lt.railgun.structural_core_required");
            return;
        }
        RailgunModuleEntries mods = stack.getOrDefault(ModDataComponents.RAILGUN_MODULE_ENTRIES.get(), RailgunModuleEntries.EMPTY);
        if (!mods.hasCore()) {
            sendFail(player, "ae2lt.railgun.core_required");
            return;
        }
        var bound = RailgunBinding.resolve(stack, player);
        if (!bound.success()) {
            sendFail(player, RailgunBinding.failKey(bound.failure()));
            return;
        }
        IGrid grid = bound.grid();

        RailgunSettings settings = stack.getOrDefault(ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT);
        AmmoCost cost = AmmoCost.forCharged(tier, mods);

        IActionSource src = IActionSource.ofPlayer(player);
        var inv = grid.getStorageService().getInventory();

        RailgunEnergyBuffer.refillFromNetwork(
                stack,
                player,
                Math.max(0L, cost.feEnergy() - RailgunEnergyBuffer.read(stack)));
        if (!RailgunEnergyBuffer.tryConsume(stack, player, cost.feEnergy())) {
            sendFail(player, "ae2lt.railgun.fail.no_fe");
            return;
        }
        // 2. Try EHV; fall back to HV compensation if the core module is present.
        long ehvNeeded = cost.ehv();
        long ehvGot = inv.extract(LightningKey.EXTREME_HIGH_VOLTAGE, ehvNeeded, Actionable.MODULATE, src);
        long ehvShort = ehvNeeded - ehvGot;
        if (ehvShort > 0L) {
            int compensationRatio = LightningCompensationPolicy.bestRatio(mods.capabilities());
            if (compensationRatio <= 0) {
                inv.insert(LightningKey.EXTREME_HIGH_VOLTAGE, ehvGot, Actionable.MODULATE, src);
                RailgunEnergyBuffer.refund(stack, cost.feEnergy());
                sendFail(player, "ae2lt.railgun.fail.no_ehv");
                return;
            }
            long needHv = LightningCompensationPolicy.highVoltageRequired(ehvShort, compensationRatio);
            long gotHv = inv.extract(LightningKey.HIGH_VOLTAGE, needHv, Actionable.MODULATE, src);
            if (gotHv < needHv) {
                inv.insert(LightningKey.EXTREME_HIGH_VOLTAGE, ehvGot, Actionable.MODULATE, src);
                inv.insert(LightningKey.HIGH_VOLTAGE, gotHv, Actionable.MODULATE, src);
                RailgunEnergyBuffer.refund(stack, cost.feEnergy());
                sendFail(player, "ae2lt.railgun.fail.no_compensation_hv");
                return;
            }
        }
        // 3. Raycast first hit.
        Vec3 from = player.getEyePosition();
        double range = RailgunDefaults.CHARGED_RANGE;
        Vec3 dir = player.getLookAngle();
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult bhr = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 endBlock = bhr.getType() == HitResult.Type.MISS ? to : bhr.getLocation();
        EntityHitResult ehr = ProjectileUtil.getEntityHitResult(level, player, from, endBlock, new AABB(from, endBlock).inflate(1.0D),
                e -> e instanceof LivingEntity le
                        && le != player
                        && !le.isSpectator()
                        && (settings.pvp() || !(le instanceof Player)));

        DamageContext ctx = DamageContext.buildCharged(player, tier, mods, level, settings.pvp());
        Vec3 firstHitPos = ehr != null ? ehr.getLocation() : endBlock;
        List<RailgunChainResolver.Hit> hits = new ArrayList<>();
        int primaryId = -1;
        int chainTuningCount = countChainTuning(mods);
        double effectivePulseRadius = tier.isMax()
                ? RailgunDefaults.PULSE_RADIUS + 1.5D * chainTuningCount
                : 0.0D;
        double effectivePulseRatio = switch (chainTuningCount) {
            case 0 -> RailgunDefaults.PULSE_DAMAGE_RATIO;
            case 1 -> 0.85D;
            default -> 1.0D;
        };
        if (ehr != null && ehr.getEntity() instanceof LivingEntity primary) {
            primaryId = primary.getId();
            hits.add(new RailgunChainResolver.Hit(primary, ctx.firstDamage(), false, false));
            hits.addAll(RailgunChainResolver.resolveChainForkedFrom(
                    level, player, primary, ctx, Set.of(), null));
            if (tier.isMax()) {
                hits.addAll(RailgunChainResolver.resolvePenetration(level, player, primary, ctx,
                        RailgunDefaults.PENETRATION_MAX_TARGETS));
                hits.addAll(RailgunChainResolver.resolvePulse(level, player, primary.position(),
                        effectivePulseRadius, effectivePulseRatio, ctx, primaryId));
            }
        }
        // Impact splash AOE — fires whether or not we hit a target directly. Skips primary
        // (so direct-hit targets don't get double-dipped) and uses pulse-flag semantics.
        double impactRadius = switch (tier) {
            case EHV1 -> RailgunDefaults.IMPACT_RADIUS_TIER1;
            case EHV2 -> RailgunDefaults.IMPACT_RADIUS_TIER2;
            case EHV3 -> RailgunDefaults.IMPACT_RADIUS_TIER3;
            default -> 0.0D;
        };
        double impactRatio = switch (tier) {
            case EHV1 -> RailgunDefaults.IMPACT_DAMAGE_RATIO_TIER1;
            case EHV2 -> RailgunDefaults.IMPACT_DAMAGE_RATIO_TIER2;
            case EHV3 -> RailgunDefaults.IMPACT_DAMAGE_RATIO_TIER3;
            default -> 0.0D;
        };
        if (impactRadius > 0.0D && impactRatio > 0.0D) {
            hits.addAll(RailgunChainResolver.resolveImpactSplash(
                    level, player, firstHitPos, impactRadius, impactRatio, primaryId, ctx));
        }
        // Splash-triggered chain: if the splash hit at least one enemy, pick the closest
        // splash victim and chain from there. All previously-hit IDs (primary + primary
        // chain + penetration + pulse + splash) are excluded so the splash chain only
        // jumps to fresh targets. The first arc visually starts at the impact center.
        LivingEntity splashAnchor = findClosestSplashAnchor(hits, firstHitPos, primaryId);
        if (splashAnchor != null) {
            Set<Integer> alreadyHit = new HashSet<>();
            for (var h : hits) {
                if (h.target() != null) alreadyHit.add(h.target().getId());
            }
            hits.addAll(RailgunChainResolver.resolveChainForkedFrom(
                    level, player, splashAnchor, ctx, alreadyHit, firstHitPos));
        }
        if (!hits.isEmpty()) {
            applyAll(level, player, hits, ctx, stack, tier);
        }

        // 4. Terrain
        if (AE2LTCommonConfig.railgunTerrainDestructionEnabled() && settings.terrainDestruction()) {
            RailgunTerrainService.queueDestroy(level, firstHitPos, tier, player, stack);
            if (tier.isMax()) {
                List<Vec3> tunnel = new ArrayList<>();
                for (var h : hits) {
                    if (h.penetration()) tunnel.add(h.target().position());
                }
                if (!tunnel.isEmpty()) {
                    RailgunTerrainService.queueDestroyAlongPath(level, tunnel, player, stack);
                }
            }
        }

        // 5. Recoil
        RailgunRecoilService.apply(player, tier);

        // 6. Broadcast client FX
        broadcastFire(level, player, from, firstHitPos, tier, hits, effectivePulseRadius, settings.soundEnabled());
    }

    public static void applyAll(ServerLevel level, ServerPlayer player, List<RailgunChainResolver.Hit> hits, DamageContext ctx) {
        applyAll(level, player, hits, ctx, ItemStack.EMPTY, RailgunChargeTier.HV);
    }

    public static void applyAll(ServerLevel level, ServerPlayer player, List<RailgunChainResolver.Hit> hits,
                                DamageContext ctx, ItemStack railgunStack, RailgunChargeTier tier) {
        if (hits.isEmpty()) return;
        Holder<net.minecraft.world.damagesource.DamageType> damageHolder =
                ModDamageTypes.electromagneticHolder(level);
        DamageSource ds = new DamageSource(damageHolder, player, player);
        boolean damagePlayers = AE2LTCommonConfig.railgunDamagePlayers();
        boolean paralyzePlayers = AE2LTCommonConfig.railgunParalysisOnPlayers();
        int paralysisDur = RailgunDefaults.PARALYSIS_DURATION_TICKS;
        boolean overloadEligible = tier == RailgunChargeTier.EHV3 && !railgunStack.isEmpty();

        for (var hit : hits) {
            LivingEntity target = hit.target();
            if (target == null || !target.isAlive()) continue;
            if (target instanceof Player tp) {
                if (!damagePlayers) continue;
                if (!ctx.pvp()) continue;
                if (tp.isCreative() || tp.isSpectator()) continue;
            }
            double armorReduction = DamageContext.effectiveArmorReduction(target);
            double finalDamage = DamageContext.finalDamage(hit.damage(), ctx.bypassRatio(), armorReduction);
            target.invulnerableTime = 0; // bypass i-frames for charged shots? keep for fairness on beam, allow here.
            target.hurt(ds, (float) finalDamage);
            if (target instanceof Player && !paralyzePlayers) continue;
            if (paralysisDur > 0) {
                target.addEffect(new MobEffectInstance(
                        ModMobEffects.ELECTROMAGNETIC_PARALYSIS,
                        paralysisDur,
                        0,
                        false,
                        true,
                        true), player);
            }
            // Overload Execution: only on EHv3 charged shots with the OVERLOAD module
            // installed. The service itself re-checks the module + master switch.
            if (overloadEligible) {
                OverloadExecutionService.onHit(level, player, railgunStack, target, finalDamage);
            }
        }
    }

    private static void broadcastFire(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 firstHit,
                                      RailgunChargeTier tier, List<RailgunChainResolver.Hit> hits,
                                      double effectivePulseRadius, boolean soundEnabled) {
        List<Vec3> chainPath = new ArrayList<>();
        Vec3 prev = firstHit;
        for (var h : hits) {
            if (h.penetration() || h.pulse()) continue;
            // A hit may carry an explicit chain-start override (used for splash-triggered
            // chains so the arc visually emanates from the impact center, not from the tail
            // of the primary chain).
            if (h.chainStartAt() != null) {
                prev = h.chainStartAt();
            }
            Vec3 cur = h.target().position().add(0.0D, h.target().getBbHeight() / 2.0D, 0.0D);
            chainPath.add(prev);
            chainPath.add(cur);
            prev = cur;
        }
        // Total radius for shockwave: impact splash + (max-tier) EMP pulse, picking the bigger.
        double impactR = switch (tier) {
            case EHV1 -> RailgunDefaults.IMPACT_RADIUS_TIER1;
            case EHV2 -> RailgunDefaults.IMPACT_RADIUS_TIER2;
            case EHV3 -> RailgunDefaults.IMPACT_RADIUS_TIER3;
            default -> 0.0D;
        };
        if (tier.isMax()) {
            impactR = Math.max(impactR, effectivePulseRadius);
        }
        var pkt = new RailgunFirePacket(
                player.getUUID(), from, firstHit, chainPath, tier.ordinal(), tier.isMax(), soundEnabled, (float) impactR);
        NetworkHandler.sendToTrackingChunk(level, player.chunkPosition(), pkt);
    }

    /**
     * Pick the closest splash victim to the impact point as the anchor for a splash-triggered
     * chain. Splash victims are flagged with {@code pulse=true} via
     * {@link RailgunChainResolver#resolveImpactSplash}; the primary direct hit (if any) is
     * excluded.
     */
    @Nullable
    private static LivingEntity findClosestSplashAnchor(
            List<RailgunChainResolver.Hit> hits, Vec3 impactCenter, int primaryId) {
        LivingEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (var h : hits) {
            if (!h.pulse()) continue;
            LivingEntity t = h.target();
            if (t == null || !t.isAlive()) continue;
            if (t.getId() == primaryId) continue;
            double dSq = t.position().distanceToSqr(impactCenter);
            if (dSq < bestSq) {
                bestSq = dSq;
                best = t;
            }
        }
        return best;
    }

    public static void sendFail(@Nullable ServerPlayer player, String key) {
        if (player == null) return;
        player.displayClientMessage(Component.translatable(key), true);
    }

    private static int countAccelerationFactor(RailgunModuleEntries mods) {
        int n = 0;
        for (var cap : mods.capabilities()) {
            if (cap instanceof DeviceCapability.AccelerationFactor) n++;
        }
        return n;
    }

    private static int countChainTuning(RailgunModuleEntries mods) {
        int n = 0;
        for (var cap : mods.capabilities()) {
            if (cap instanceof DeviceCapability.ChainTuning) n++;
        }
        return n;
    }
}
