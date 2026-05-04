package com.moakiee.ae2lt.logic.railgun;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.item.railgun.RailgunModules;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.network.NetworkHandler;
import com.moakiee.ae2lt.network.railgun.RailgunBeamChainFxPacket;
import com.moakiee.ae2lt.network.railgun.RailgunBeamUpdatePacket;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModDamageTypes;

/**
 * State machine for the held-left-click beam. One {@link BeamState} per
 * actively-firing player. Resources are deducted on every settle interval
 * (default 2 ticks); chain jumps fire at most every {@code chainThrottleTicks}.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class RailgunBeamService {

    /** Mutable per-player beam state. */
    public static final class BeamState {
        private final InteractionHand hand;
        private int lastSettleTick;
        private int lastChainTick;
        private int settleCount;

        BeamState(InteractionHand hand, int now) {
            this.hand = hand;
            this.lastSettleTick = now;
            this.lastChainTick = now;
            this.settleCount = 0;
        }
        public InteractionHand hand() { return hand; }
    }

    private record BeamTrace(Vec3 from, Vec3 endPoint, EntityHitResult entityHit) {}

    private static final Map<UUID, BeamState> ACTIVE = new ConcurrentHashMap<>();

    private RailgunBeamService() {}

    public static void setFiring(ServerPlayer player, InteractionHand hand, boolean firing) {
        if (firing) {
            int max = RailgunDefaults.BEAM_MAX_CONCURRENT;
            if (ACTIVE.size() >= max && !ACTIVE.containsKey(player.getUUID())) {
                broadcastStop(player);
                return;
            }
            ACTIVE.put(player.getUUID(), new BeamState(hand, player.tickCount));
        } else {
            BeamState removed = ACTIVE.remove(player.getUUID());
            if (removed != null) {
                broadcastStop(player);
            }
        }
    }

    public static boolean isFiring(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        if (ACTIVE.isEmpty()) return;
        var server = e.getServer();
        Iterator<Map.Entry<UUID, BeamState>> it = ACTIVE.entrySet().iterator();
        int settleInterval = Math.max(1, RailgunDefaults.BEAM_SETTLE_INTERVAL_TICKS);
        int chainThrottle = Math.max(1, RailgunDefaults.BEAM_CHAIN_THROTTLE_TICKS);
        while (it.hasNext()) {
            var entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            BeamState s = entry.getValue();
            if (player == null || player.isDeadOrDying() || player.isRemoved()) {
                it.remove();
                continue;
            }
            ItemStack stack = player.getItemInHand(s.hand);
            if (!(stack.getItem() instanceof ElectromagneticRailgunItem)) {
                it.remove();
                broadcastStop(player);
                continue;
            }
            // Cannot fire beam while charged-using.
            if (player.isUsingItem() && player.getUseItem() == stack) {
                it.remove();
                broadcastStop(player);
                continue;
            }
            if (player.tickCount - s.lastSettleTick < settleInterval) {
                // Settle (every 2 ticks) re-broadcasts the beam; client STALE_TICKS=6
                // covers the gap. No keepalive needed between settles.
                continue;
            }
            s.lastSettleTick = player.tickCount;
            s.settleCount++;
            boolean cont = settle((ServerLevel) player.level(), player, stack, s, chainThrottle);
            if (!cont) {
                it.remove();
                broadcastStop(player);
            }
        }
    }

    private static boolean settle(ServerLevel level, ServerPlayer player, ItemStack stack,
                                  BeamState s, int chainThrottle) {
        var bound = RailgunBinding.resolve(stack, player);
        if (!bound.success()) {
            RailgunFireService.sendFail(player, RailgunBinding.failKey(bound.failure()));
            return false;
        }
        IGrid grid = bound.grid();
        RailgunModules mods = stack.getOrDefault(ModDataComponents.RAILGUN_MODULES.get(), RailgunModules.EMPTY);
        RailgunSettings settings = stack.getOrDefault(ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT);

        long aeCost = AmmoCost.beamAeCost(mods);
        IActionSource src = IActionSource.ofPlayer(player);
        int interval = AmmoCost.beamHvCostInterval(mods);
        // The HV pull is sampled BEFORE the AE buffer deduction so that we don't
        // half-pay (deduct AE then fail HV). If HV is required but unavailable,
        // we fail immediately without touching the buffer or the network.
        long pendingHv = 0L;
        if (s.settleCount % interval == 0) {
            long got = grid.getStorageService().getInventory().extract(
                    LightningKey.HIGH_VOLTAGE, 1L, Actionable.SIMULATE, src);
            if (got < 1L) {
                RailgunFireService.sendFail(player, "ae2lt.railgun.fail.no_hv");
                return false;
            }
            pendingHv = 1L;
        }
        if (!RailgunEnergyBuffer.tryConsume(stack, player, aeCost)) {
            RailgunFireService.sendFail(player, "ae2lt.railgun.fail.no_ae");
            return false;
        }
        if (pendingHv > 0L) {
            long got = grid.getStorageService().getInventory().extract(
                    LightningKey.HIGH_VOLTAGE, pendingHv, Actionable.MODULATE, src);
            if (got < pendingHv) {
                // Should be impossible after a successful SIMULATE in the same tick,
                // but be defensive: refund AE so we never half-charge the player.
                RailgunEnergyBuffer.refund(stack, aeCost);
                RailgunFireService.sendFail(player, "ae2lt.railgun.fail.no_hv");
                return false;
            }
        }

        // Raycast & damage
        BeamTrace trace = traceBeam(level, player);
        EntityHitResult ehr = trace.entityHit();

        if (ehr != null && ehr.getEntity() instanceof LivingEntity primary) {
            DamageContext ctx = DamageContext.buildBeam(player, mods, level, settings.pvpLock());
            // Primary hit
            DamageSource ds = beamDamageSource(level, player);
            double armorReduction = DamageContext.effectiveArmorReduction(primary);
            double finalDamage = DamageContext.finalDamage(ctx.firstDamage(), ctx.bypassRatio(), armorReduction);
            primary.hurt(ds, (float) finalDamage);
            if (primary.isAlive() && RailgunDefaults.PARALYSIS_DURATION_TICKS > 0) {
                primary.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        com.moakiee.ae2lt.registry.ModMobEffects.ELECTROMAGNETIC_PARALYSIS,
                        RailgunDefaults.PARALYSIS_DURATION_TICKS,
                        0, false, true, true), player);
            }
            // Throttled chain
            if (player.tickCount - s.lastChainTick >= chainThrottle) {
                s.lastChainTick = player.tickCount;
                List<RailgunChainResolver.Hit> chain = RailgunChainResolver.resolveChain(level, player, primary, ctx);
                RailgunFireService.applyAll(level, player, chain, ctx);
                if (!chain.isEmpty()) {
                    broadcastBeamChainFx(level, player, primary, chain);
                }
            }
        }

        // Broadcast beam segment
        broadcastTrace(level, player, trace);
        return true;
    }

    private static BeamTrace traceBeam(ServerLevel level, ServerPlayer player) {
        Vec3 from = player.getEyePosition();
        double range = RailgunDefaults.BEAM_RANGE;
        Vec3 dir = player.getLookAngle();
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult bhr = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 endBlock = bhr.getType() == HitResult.Type.MISS ? to : bhr.getLocation();
        EntityHitResult ehr = ProjectileUtil.getEntityHitResult(level, player, from, endBlock,
                new AABB(from, endBlock).inflate(0.5D),
                e -> e instanceof LivingEntity le && le != player && !le.isSpectator());
        Vec3 endPoint = ehr != null ? ehr.getLocation() : endBlock;
        return new BeamTrace(from, endPoint, ehr);
    }

    private static void broadcastTrace(ServerLevel level, ServerPlayer player, BeamTrace trace) {
        var pkt = new RailgunBeamUpdatePacket(player.getUUID(), trace.from(), trace.endPoint(), true);
        NetworkHandler.sendToTrackingChunk(level, player.chunkPosition(), pkt);
    }

    /**
     * Broadcast a one-shot chain visual for the beam's throttled chain jump.
     * Each consecutive (a, b) pair in {@code chainPath} forms one arc segment;
     * the first arc starts at the primary target's hit center.
     *
     * <p>This was missing before — the server applied chain damage but never
     * told the client to render it, so chained enemies took silent hits with
     * no electric arcs between them. Same encoding as {@code RailgunFirePacket}
     * so the client renderer can reuse {@link com.moakiee.ae2lt.client.railgun.RailgunArcRenderer#spawnChain}.
     */
    private static void broadcastBeamChainFx(ServerLevel level, ServerPlayer player,
                                             LivingEntity primary,
                                             List<RailgunChainResolver.Hit> chain) {
        java.util.List<Vec3> path = new java.util.ArrayList<>(chain.size() * 2);
        Vec3 prev = primary.position().add(0.0D, primary.getBbHeight() / 2.0D, 0.0D);
        Vec3 firstHit = prev;
        for (var h : chain) {
            if (h.target() == null) continue;
            Vec3 cur = h.target().position().add(0.0D, h.target().getBbHeight() / 2.0D, 0.0D);
            path.add(prev);
            path.add(cur);
            prev = cur;
        }
        if (path.isEmpty()) return;
        var pkt = new RailgunBeamChainFxPacket(player.getUUID(), firstHit, path);
        NetworkHandler.sendToTrackingChunk(level, player.chunkPosition(), pkt);
    }

    private static DamageSource beamDamageSource(ServerLevel level, ServerPlayer player) {
        Holder<net.minecraft.world.damagesource.DamageType> h = ModDamageTypes.electromagneticHolder(level);
        return new DamageSource(h, player, player);
    }

    private static void broadcastStop(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        var pkt = new RailgunBeamUpdatePacket(player.getUUID(), Vec3.ZERO, Vec3.ZERO, false);
        NetworkHandler.sendToTrackingChunk(sl, player.chunkPosition(), pkt);
    }
}
