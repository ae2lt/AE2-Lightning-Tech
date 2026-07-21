package com.moakiee.ae2lt.logic.railgun;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.gameevent.GameEvent;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.item.railgun.RailgunEnergyRules;
import com.moakiee.ae2lt.item.railgun.RailgunModuleEntries;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorUndyingHandler;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModDamageTypes;

/**
 * Overload Execution — "I remember you" HP-record model.
 *
 * <p><b>Activation:</b> the {@link RailgunModuleEntries#hasOverloadExecution()} flag
 * plus the {@code overloadExecution.enabled} config switch. Trigger is gated by the
 * caller (currently only EHv3 charged shots in {@code RailgunFireService.applyAll}).
 *
 * <p><b>Model:</b> per railgun ItemStack, a small list of {@code (uuid, recordedHp,
 * lastHitTick)} entries is kept on {@link DataComponents#CUSTOM_DATA}. On each
 * qualifying hit the basis HP is computed as:
 * <pre>
 *   elapsed = now - lastHitTick
 *   if elapsed &gt;= decayWindow:
 *       entry purged, basis = currentHp
 *   else:
 *       x = elapsed / decayWindow             ∈ [0, 1]
 *       f = x ^ decayPower                    ∈ [0, 1]   // slow start, fast end
 *       restored = min(recordedHp + (maxHp - recordedHp) * f, currentHp)
 *       basis = restored
 *
 *   finalHp = basis - damage
 *
 *   if finalHp &gt; 0:
 *       if currentHp &gt; finalHp:
 *           target.setHealth(finalHp)         // direct write, bypass i-frames
 *       record (uuid, finalHp, now)
 *   else:
 *       execute(target, configuredMode)
 *       remove entry
 * </pre>
 *
 * <p>This replaces the older "cumulative damage accumulator" model. The HP-record
 * model is self-cleaning: kills always purge the entry (force-kill path explicitly
 * removes; direct-write path purges if the target died from the shot). Other-source
 * deaths are left for the 60-second decay to wash out.
 *
 * <p>Execution has two user-selectable modes. Normal death completes the already-started
 * damage flow with a lethal health write and the target's own
 * {@link LivingEntity#die(DamageSource)}, then leaves removal entirely to its death tick.
 * Forced removal runs the complete death, kill, discard and remove cleanup chain before
 * a final removal fallback. It gives normal loot settlement the first opportunity but
 * does not treat settlement success as an alternative to guaranteed removal.
 * Neither path names or depends on a specific boss implementation.
 */
public final class OverloadExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger("ae2lt/OverloadExecution");
    private static final String TAG_TARGETS = "OverloadExecutionTargets";
    private static final String TAG_UUID = "uuid";
    private static final String TAG_RECORDED_HP = "recordedHp";
    private static final String TAG_LAST_HIT_TICK = "lastHitTick";

    private OverloadExecutionService() {}

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Called from {@code RailgunFireService.applyAll} when an EHv3 charged shot hits
     * a living entity with the OVERLOAD module installed. Quietly returns when the
     * master switch is off, the module is absent, or the target is creative/spectator.
     */
    public static void onHit(ServerLevel level, ServerPlayer player, ItemStack stack,
                             LivingEntity target, double damage) {
        if (!AE2LTCommonConfig.overloadExecutionEnabled()) return;

        RailgunSettings settings = stack.getOrDefault(
                ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT);
        boolean allowPlayerTargets = settings.allowsPlayerTargets(AE2LTCommonConfig.railgunDamagePlayers());
        if (!RailgunTargetRules.canAffect(player, target, allowPlayerTargets)) return;

        RailgunModuleEntries mods = stack.getOrDefault(ModDataComponents.RAILGUN_MODULE_ENTRIES.get(), RailgunModuleEntries.EMPTY);
        if (!mods.hasOverloadExecution()) return;
        int maxTracked = AE2LTCommonConfig.overloadExecutionMaxTracked();
        int decayWindow = AE2LTCommonConfig.overloadExecutionDecayWindowTicks();
        double decayPower = AE2LTCommonConfig.overloadExecutionDecayPower();

        UUID targetUuid = target.getUUID();
        long now = level.getGameTime();
        double currentHp = target.getHealth();
        double maxHp = target.getMaxHealth();

        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag targets = root.getList(TAG_TARGETS, Tag.TAG_COMPOUND);
        int existingIdx = indexOf(targets, targetUuid);
        long feCost = RailgunEnergyRules.overloadExecutionCostFe();
        RailgunEnergyBuffer.refillFromNetwork(
                stack,
                player,
                Math.max(0L, feCost - RailgunEnergyBuffer.read(stack)));
        if (!RailgunEnergyBuffer.tryConsume(stack, player, feCost)) {
            RailgunFireService.sendFail(player, "ae2lt.railgun.fail.no_fe");
            return;
        }

        // 1. Resolve "basis HP" from the record (or current HP if no record / expired).
        double basis;
        if (existingIdx < 0) {
            basis = currentHp;
        } else {
            CompoundTag entry = targets.getCompound(existingIdx);
            double recorded = entry.getDouble(TAG_RECORDED_HP);
            long lastHit = entry.getLong(TAG_LAST_HIT_TICK);
            long elapsed = now - lastHit;
            if (elapsed >= decayWindow) {
                // Window expired — record is stale, drop it and start fresh.
                targets.remove(existingIdx);
                basis = currentHp;
            } else {
                double x = Math.max(0.0D, Math.min(1.0D, (double) elapsed / (double) decayWindow));
                double f = Math.pow(x, decayPower);
                double recovery = Math.max(0.0D, maxHp - recorded) * f;
                basis = Math.min(recorded + recovery, currentHp);
            }
        }

        double finalHp = basis - damage;

        // 2. Forced-kill branch.
        if (finalHp <= 0.0D) {
            // Make sure the entry is gone before executing — execute() may trigger
            // entity removal listeners that re-enter this service path.
            int idx = indexOf(targets, targetUuid);
            if (idx >= 0) targets.remove(idx);
            saveTargets(stack, root, targets);
            DamageSource ds = new DamageSource(ModDamageTypes.electromagneticHolder(level), player, player);
            execute(target, Math.max(damage, currentHp), ds, settings.forceOverloadRemoval());
            return;
        }

        // 3. Direct-write branch. Only write if the new value is actually lower than
        //    current — otherwise nothing changes (the recorded HP is "weaker" than what
        //    the target actually has, so we don't heal them down to a higher value).
        if (currentHp > finalHp) {
            // Bypass i-frames and protection: directly clamp to the recorded value.
            target.invulnerableTime = 0;
            target.setHealth((float) finalHp);
            // Visual feedback — recompute combat tracker without re-running damage rules.
            target.getCombatTracker().recordDamage(
                    new DamageSource(ModDamageTypes.electromagneticHolder(level), player, player),
                    (float) (currentHp - finalHp));
            target.hurtTime = 10;
            target.hurtDuration = 10;
            target.gameEvent(GameEvent.ENTITY_DAMAGE);

            // setHealth may itself kill the target (e.g. 0.0 floor). If so, purge.
            if (!target.isAlive()) {
                int idx = indexOf(targets, targetUuid);
                if (idx >= 0) targets.remove(idx);
                saveTargets(stack, root, targets);
                return;
            }
        }

        // 4. Update record (replace existing or append new, evict oldest beyond cap).
        CompoundTag entry = new CompoundTag();
        entry.putString(TAG_UUID, targetUuid.toString());
        entry.putDouble(TAG_RECORDED_HP, finalHp);
        entry.putLong(TAG_LAST_HIT_TICK, now);

        int idx = indexOf(targets, targetUuid);
        if (idx >= 0) {
            targets.set(idx, entry);
        } else {
            targets.add(entry);
            while (targets.size() > maxTracked) {
                targets.remove(0);
            }
        }
        saveTargets(stack, root, targets);
    }

    /**
     * Forced-execution entry point for a directly hit, non-living projectile target.
     * The ordinary damage callback has already run in {@link RailgunFireService}; this
     * method only supplies the opt-in EHv3 removal fallback and never participates in
     * chains, penetration or local-area propagation.
     */
    public static void onDirectNonLivingHit(
            ServerLevel level,
            ServerPlayer player,
            ItemStack stack,
            Entity target,
            boolean allowPlayerTargets) {
        if (!AE2LTCommonConfig.overloadExecutionEnabled()) return;
        if (target instanceof LivingEntity) return;
        if (!RailgunTargetRules.canAffect(player, target, allowPlayerTargets)) return;

        RailgunModuleEntries mods = stack.getOrDefault(
                ModDataComponents.RAILGUN_MODULE_ENTRIES.get(), RailgunModuleEntries.EMPTY);
        if (!mods.hasOverloadExecution()) return;

        RailgunSettings settings = stack.getOrDefault(
                ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT);
        if (!settings.forceOverloadRemoval()) return;

        long feCost = RailgunEnergyRules.overloadExecutionCostFe();
        RailgunEnergyBuffer.refillFromNetwork(
                stack,
                player,
                Math.max(0L, feCost - RailgunEnergyBuffer.read(stack)));
        if (!RailgunEnergyBuffer.tryConsume(stack, player, feCost)) {
            RailgunFireService.sendFail(player, "ae2lt.railgun.fail.no_fe");
            return;
        }

        forceRemoveNonLiving(target);
    }

    // ── Execution modes ─────────────────────────────────────────────────────

    private static void execute(
            LivingEntity target,
            double damage,
            DamageSource source,
            boolean forceRemoval) {
        establishKillCredit(target, source);

        // Removing a ServerPlayer corrupts the normal respawn lifecycle. The force
        // switch therefore remains a mob-removal mode; player targets always use the
        // normal death route and retain the vanilla death packet/respawn flow.
        if (forceRemoval && !(target instanceof ServerPlayer)) {
            forceRemove(target, source, (float) damage);
        } else {
            completeNormalDeath(target, source, (float) damage);
        }
    }

    /**
     * Runs a robust normal death. Damage interception is skipped, but the entity's
     * dynamic {@code die} override is retained so dungeon cleanup, advancements and
     * other entity-owned completion logic run exactly once.
     */
    private static void completeNormalDeath(LivingEntity target, DamageSource source, float damage) {
        int playerDeathsBefore = deathCount(target);
        prepareLethalState(target, source, damage);

        try {
            target.die(source);
        } catch (RuntimeException | LinkageError error) {
            LOGGER.warn("Target death callback failed for {}; applying normal-settlement fallback",
                    target.getType(), error);
        }
        if (CelestweaveArmorUndyingHandler.wasProtectedThisTick(target)) return;
        if (normalDeathCompleted(target, playerDeathsBefore)) return;

        if (target instanceof ServerPlayer player) {
            // A canceled player death cannot safely use Entity#remove: doing so leaves
            // the client without the combat-death packet and makes respawn impossible.
            // Keep the connection valid and let the protection mod own the cancellation.
            if (player.isDeadOrDying()) {
                player.setHealth(1.0F);
            }
            return;
        }

        try {
            forceDie(target, source);
        } catch (RuntimeException | LinkageError error) {
            // dead is committed before loot callbacks, so the normal death tick still
            // removes the entity even when a third-party loot hook fails.
            LOGGER.warn("Normal-settlement fallback failed for {}", target.getType(), error);
        }
    }

    /**
     * Invokes every generic cleanup layer even when an earlier layer already marked the
     * entity removed. Forced mode deliberately favors complete third-party cleanup and a
     * guaranteed final state over avoiding repeated, normally idempotent removal hooks.
     */
    private static void forceRemove(LivingEntity target, DamageSource source, float damage) {
        prepareLethalState(target, source, damage);
        try {
            target.die(source);
        } catch (RuntimeException | LinkageError error) {
            LOGGER.warn("Target death callback failed for {}; continuing forced removal",
                    target.getType(), error);
        }

        try {
            target.kill();
        } catch (RuntimeException | LinkageError error) {
            LOGGER.warn("Target kill callback failed for {}; continuing forced removal",
                    target.getType(), error);
        }

        try {
            target.discard();
        } catch (RuntimeException | LinkageError error) {
            LOGGER.warn("Target discard callback failed for {}; continuing forced removal",
                    target.getType(), error);
        }

        try {
            target.remove(Entity.RemovalReason.KILLED);
        } catch (RuntimeException | LinkageError error) {
            LOGGER.warn("Target remove callback failed for {}; applying final removal",
                    target.getType(), error);
        }
        if (!target.isRemoved()) {
            target.setRemoved(Entity.RemovalReason.KILLED);
        }
    }

    /**
     * Generic forced cleanup for directly hit entities without a LivingEntity death
     * state. Calling the dynamic kill override first preserves entity-owned settlement
     * callbacks; the remaining layers guarantee removal when that override refuses it.
     */
    private static void forceRemoveNonLiving(Entity target) {
        try {
            target.kill();
        } catch (RuntimeException | LinkageError error) {
            LOGGER.warn("Non-living target kill callback failed for {}; continuing forced removal",
                    target.getType(), error);
        }

        try {
            target.discard();
        } catch (RuntimeException | LinkageError error) {
            LOGGER.warn("Non-living target discard callback failed for {}; continuing forced removal",
                    target.getType(), error);
        }

        try {
            target.remove(Entity.RemovalReason.KILLED);
        } catch (RuntimeException | LinkageError error) {
            LOGGER.warn("Non-living target remove callback failed for {}; applying final removal",
                    target.getType(), error);
        }
        if (!target.isRemoved()) {
            target.setRemoved(Entity.RemovalReason.KILLED);
        }
    }

    private static void establishKillCredit(LivingEntity target, DamageSource source) {
        if (source.getEntity() instanceof LivingEntity attacker) {
            target.setLastHurtByMob(attacker);
        }
        if (source.getEntity() instanceof Player player) {
            target.setLastHurtByPlayer(player);
        }
    }

    private static int deathCount(LivingEntity target) {
        if (target instanceof ServerPlayer player) {
            return player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
        }
        return -1;
    }

    private static boolean normalDeathCompleted(LivingEntity target, int playerDeathsBefore) {
        if (target.dead || target.isRemoved()) return true;
        return target instanceof ServerPlayer player
                && player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)) > playerDeathsBefore;
    }

    /** Establishes a lethal combat state without entering the interceptable damage pipeline. */
    private static void prepareLethalState(LivingEntity victim, DamageSource source, float amount) {
        if (victim.level().isClientSide) return;
        if (victim.isSleeping()) victim.stopSleeping();

        victim.setNoActionTime(0);
        victim.walkAnimation.setSpeed(1.5F);
        victim.lastHurt = amount;
        victim.invulnerableTime = 0;
        victim.getCombatTracker().recordDamage(source, amount);
        victim.setHealth(0.0F);
        victim.gameEvent(GameEvent.ENTITY_DAMAGE);
        victim.hurtDuration = 10;
        victim.hurtTime = victim.hurtDuration;
    }

    /** Direct normal-settlement fallback used only when the dynamic death callback was canceled. */
    private static void forceDie(LivingEntity victim, DamageSource source) {
        if (victim.isRemoved() || victim.dead) return;

        LivingEntity killer = source.getEntity() instanceof LivingEntity attacker
                ? attacker
                : victim.getKillCredit();
        if (victim.deathScore >= 0 && killer != null) {
            killer.awardKillScore(victim, victim.deathScore, source);
        }
        if (victim.isSleeping()) victim.stopSleeping();

        victim.dead = true;
        victim.getCombatTracker().recheckStatus();

        if (victim.level() instanceof ServerLevel sl) {
            Entity srcEntity = source.getEntity();
            if (srcEntity == null || srcEntity.killedEntity(sl, victim)) {
                victim.gameEvent(GameEvent.ENTITY_DIE);
                victim.dropAllDeathLoot(sl, source);
            }
            sl.broadcastEntityEvent(victim, (byte) 3);
        }
        victim.setPose(Pose.DYING);
    }

    // ── NBT Helpers ─────────────────────────────────────────────────────────

    private static int indexOf(ListTag targets, UUID uuid) {
        String uuidStr = uuid.toString();
        for (int i = 0; i < targets.size(); i++) {
            if (uuidStr.equals(targets.getCompound(i).getString(TAG_UUID))) return i;
        }
        return -1;
    }

    private static void saveTargets(ItemStack stack, CompoundTag root, ListTag targets) {
        // Re-attach the list to the working root, then publish via CustomData.update.
        if (targets.isEmpty()) {
            root.remove(TAG_TARGETS);
        } else {
            root.put(TAG_TARGETS, targets);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (targets.isEmpty()) {
                tag.remove(TAG_TARGETS);
            } else {
                tag.put(TAG_TARGETS, targets);
            }
        });
    }
}
