package com.moakiee.ae2lt.logic.railgun;

import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.entity.PartEntity;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.item.railgun.RailgunEnergyRules;
import com.moakiee.ae2lt.item.railgun.RailgunModuleEntries;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorUndyingHandler;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModDamageTypes;
import com.moakiee.ae2lt.registry.ModMobEffects;

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
 *       executeKill(target)                   // 5-layer force-die
 *       remove entry
 * </pre>
 *
 * <p>This replaces the older "cumulative damage accumulator" model. The HP-record
 * model is self-cleaning: kills always purge the entry (force-kill path explicitly
 * removes; direct-write path purges if the target died from the shot). Other-source
 * deaths are left for the 60-second decay to wash out.
 *
 * <p>The forced-kill sequence below is unchanged — inspired by Avaritia's Infinity
 * Sword bypass strategy.
 */
public final class OverloadExecutionService {

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
        if (!target.isAlive()) return;
        if (target instanceof Player tp && (tp.isCreative() || tp.isSpectator())) return;

        RailgunModuleEntries mods = stack.getOrDefault(ModDataComponents.RAILGUN_MODULE_ENTRIES.get(), RailgunModuleEntries.EMPTY);
        if (!mods.hasOverloadExecution()) return;

        RailgunSettings settings = stack.getOrDefault(ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT);
        if (!settings.pvp() && target instanceof Player) return;
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
            execute(level, target, Math.max(damage, currentHp), ds, player);
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

    // ── Execution Sequence (5-layer forced kill) ────────────────────────────

    private static void execute(ServerLevel level, LivingEntity target, double damage,
                                DamageSource source, ServerPlayer player) {
        // Boss pre-processing.
        if (target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) {
            dragon.hurt(dragon.head, source, (float) damage);
            if (!target.isAlive()) return;
        } else if (target instanceof WitherBoss wither) {
            wither.setInvulnerableTicks(0);
        }

        // Layer 1: simulate massive player damage through vanilla pipeline.
        target.invulnerableTime = 0;
        target.hurt(source, (float) damage);
        if (CelestweaveArmorUndyingHandler.wasProtectedThisTick(target)) return;
        if (!target.isAlive()) return;

        // Layer 2: directHurt — bypasses armor, enchantments, potions, events.
        directHurt(target, source, (float) damage);
        if (CelestweaveArmorUndyingHandler.wasProtectedThisTick(target)) return;
        if (!target.isAlive()) return;

        // Layer 3: kill().
        target.kill();
        if (CelestweaveArmorUndyingHandler.wasProtectedThisTick(target)) return;
        if (!target.isAlive()) return;

        // Layer 4: setHealth(0).
        target.setHealth(0.0F);
        if (CelestweaveArmorUndyingHandler.wasProtectedThisTick(target)) return;
        if (!target.isAlive()) return;

        // Layer 5: forceDie() — directly set dead = true.
        forceDie(target, source);

        if (!target.isAlive()) {
            player.killedEntity(level, target);
        }
    }

    /** Bypasses the entire vanilla damage pipeline. Inspired by Avaritia InfinitySwordItem.hurt(). */
    private static boolean directHurt(LivingEntity victim, DamageSource source, float amount) {
        if (victim.level().isClientSide || victim.isDeadOrDying()) return false;

        if (victim.isMultipartEntity()) {
            for (Entity part : victim.getParts()) {
                if (part instanceof PartEntity<?> pe && pe.getParent() == victim) {
                    part.hurt(source, amount);
                }
            }
        }
        if (victim.isSleeping()) victim.stopSleeping();

        victim.setNoActionTime(0);
        victim.walkAnimation.setSpeed(1.5F);
        victim.lastHurt = amount;
        victim.invulnerableTime = 0;
        victim.getCombatTracker().recordDamage(source, amount);
        victim.setHealth(victim.getHealth() - amount);
        victim.gameEvent(GameEvent.ENTITY_DAMAGE);
        victim.hurtDuration = 10;
        victim.hurtTime = victim.hurtDuration;

        if (RailgunDefaults.PARALYSIS_DURATION_TICKS > 0) {
            victim.addEffect(new MobEffectInstance(
                            ModMobEffects.ELECTROMAGNETIC_PARALYSIS,
                            RailgunDefaults.PARALYSIS_DURATION_TICKS, 0, false, true, true),
                    source.getEntity() instanceof LivingEntity le ? le : null);
        }

        if (victim.isDeadOrDying()) {
            forceDie(victim, source);
        }
        return true;
    }

    /** Directly sets dead = true and drops loot. Inspired by Avaritia InfinitySwordItem.die(). */
    private static void forceDie(LivingEntity victim, DamageSource source) {
        if (victim.isRemoved() || victim.dead) return;

        LivingEntity killer = victim.getKillCredit();
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
