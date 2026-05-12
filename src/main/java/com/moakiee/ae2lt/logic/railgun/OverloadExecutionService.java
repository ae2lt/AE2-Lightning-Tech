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

import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.item.railgun.RailgunModules;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModDamageTypes;
import com.moakiee.ae2lt.registry.ModMobEffects;

/**
 * Overload execution: tracks cumulative damage per target on the railgun NBT,
 * triggers a multi-layer forced-kill sequence when accumulated >= target HP.
 * Inspired by Avaritia's Infinity Sword bypass strategy.
 */
public final class OverloadExecutionService {

    private static final String TAG_TARGETS = "OverloadExecutionTargets";
    private static final String TAG_UUID = "uuid";
    private static final String TAG_ACCUMULATED = "accumulated";
    private static final String TAG_LAST_HIT_TICK = "lastHitTick";

    private OverloadExecutionService() {}

    // ── Public API ──────────────────────────────────────────────────────────

    /** Called after each hit in RailgunFireService / RailgunBeamService. */
    public static void onHit(ServerLevel level, ServerPlayer player, ItemStack stack,
                             LivingEntity target, double actualDamage) {
        if (!target.isAlive()) return;
        if (target instanceof Player tp && (tp.isCreative() || tp.isSpectator())) return;

        RailgunModules mods = stack.getOrDefault(ModDataComponents.RAILGUN_MODULES.get(), RailgunModules.EMPTY);
        if (!mods.hasOverloadExecution()) return;

        RailgunSettings settings = stack.getOrDefault(ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT);
        if (settings.pvpLock() && target instanceof Player) return;

        OverloadExecutionTuningParams params = readTuning(mods);
        UUID targetUuid = target.getUUID();
        long now = level.getGameTime();

        // Read current accumulator
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag targets = root.getList(TAG_TARGETS, Tag.TAG_COMPOUND);

        // Find or create entry
        CompoundTag entry = findEntry(targets, targetUuid);
        double accumulated = entry.getDouble(TAG_ACCUMULATED) + actualDamage;
        entry.putDouble(TAG_ACCUMULATED, accumulated);
        entry.putLong(TAG_LAST_HIT_TICK, now);

        // Ensure entry is in list (new entries are appended)
        if (!containsUuid(targets, targetUuid)) {
            targets.add(entry);
        }

        // Evict oldest if over max
        while (targets.size() > params.maxTracked) {
            targets.remove(0);
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(TAG_TARGETS, targets));

        // Check execution threshold
        if (accumulated >= target.getHealth()) {
            DamageSource ds = new DamageSource(ModDamageTypes.electromagneticHolder(level), player, player);
            execute(level, target, accumulated, ds, player);
        }
    }

    /** Per-tick decay for all tracked targets on a held railgun. */
    public static void tickDecay(ServerPlayer player, ItemStack stack, ServerLevel level) {
        RailgunModules mods = stack.getOrDefault(ModDataComponents.RAILGUN_MODULES.get(), RailgunModules.EMPTY);
        if (!mods.hasOverloadExecution()) return;

        OverloadExecutionTuningParams params = readTuning(mods);
        long now = level.getGameTime();

        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag targets = root.getList(TAG_TARGETS, Tag.TAG_COMPOUND);

        boolean changed = false;
        for (int i = targets.size() - 1; i >= 0; i--) {
            CompoundTag entry = targets.getCompound(i);
            long lastHit = entry.getLong(TAG_LAST_HIT_TICK);
            double acc = entry.getDouble(TAG_ACCUMULATED);

            if (now - lastHit > params.decayDelayTicks && acc > 0) {
                acc *= (1.0 - params.decayRate);
                if (acc < 0.1) {
                    targets.remove(i);
                } else {
                    entry.putDouble(TAG_ACCUMULATED, acc);
                }
                changed = true;
            }
        }

        if (changed) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(TAG_TARGETS, targets));
        }
    }

    // ── Execution Sequence (Avaritia-inspired) ─────────────────────────────

    private static void execute(ServerLevel level, LivingEntity target, double accumulated,
                                DamageSource source, ServerPlayer player) {
        // Boss pre-processing
        if (target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) {
            dragon.hurt(dragon.head, source, (float) accumulated);
            if (!target.isAlive()) return;
        } else if (target instanceof WitherBoss wither) {
            wither.setInvulnerableTicks(0);
        }

        // Layer 1: simulate player massive damage through vanilla pipeline
        target.invulnerableTime = 0;
        target.hurt(source, (float) accumulated);
        if (!target.isAlive()) return;

        // Layer 2: directHurt — bypasses armor, enchantments, potions, events
        directHurt(target, source, (float) accumulated);
        if (!target.isAlive()) return;

        // Layer 3: kill()
        target.kill();
        if (!target.isAlive()) return;

        // Layer 4: setHealth(0)
        target.setHealth(0.0F);
        if (!target.isAlive()) return;

        // Layer 5: forceDie() — directly set dead = true
        forceDie(target, source);

        if (!target.isAlive()) {
            player.killedEntity(level, target);
        }
    }

    /** Bypasses the entire vanilla damage pipeline. Inspired by Avaritia InfinitySwordItem.hurt(). */
    private static boolean directHurt(LivingEntity victim, DamageSource source, float amount) {
        if (victim.level().isClientSide || victim.isDeadOrDying()) return false;

        // Handle multipart entities (Ender Dragon)
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
        victim.setHealth(victim.getHealth() - amount);   // DIRECT SET — bypasses all protection
        victim.gameEvent(GameEvent.ENTITY_DAMAGE);
        victim.hurtDuration = 10;
        victim.hurtTime = victim.hurtDuration;

        // Apply paralysis
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

    private static CompoundTag findEntry(ListTag targets, UUID uuid) {
        String uuidStr = uuid.toString();
        for (int i = 0; i < targets.size(); i++) {
            CompoundTag entry = targets.getCompound(i);
            if (uuidStr.equals(entry.getString(TAG_UUID))) {
                return entry;
            }
        }
        CompoundTag entry = new CompoundTag();
        entry.putString(TAG_UUID, uuidStr);
        entry.putDouble(TAG_ACCUMULATED, 0.0);
        entry.putLong(TAG_LAST_HIT_TICK, 0L);
        return entry;
    }

    private static boolean containsUuid(ListTag targets, UUID uuid) {
        String uuidStr = uuid.toString();
        for (int i = 0; i < targets.size(); i++) {
            if (uuidStr.equals(targets.getCompound(i).getString(TAG_UUID))) return true;
        }
        return false;
    }

    // ── Tuning ──────────────────────────────────────────────────────────────

    private record OverloadExecutionTuningParams(double decayRate, int decayDelayTicks, int maxTracked) {}

    private static OverloadExecutionTuningParams readTuning(RailgunModules mods) {
        for (var cap : mods.capabilities()) {
            if (cap instanceof DeviceCapability.OverloadExecutionTuning t) {
                return new OverloadExecutionTuningParams(t.decayRate(), t.decayDelayTicks(), t.maxTrackedTargets());
            }
        }
        return new OverloadExecutionTuningParams(0.02D, 200, 8);
    }
}
