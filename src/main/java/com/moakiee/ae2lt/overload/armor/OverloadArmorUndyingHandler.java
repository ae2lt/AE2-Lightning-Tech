package com.moakiee.ae2lt.overload.armor;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem;
import com.moakiee.ae2lt.overload.armor.module.UndyingSubmodule;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class OverloadArmorUndyingHandler {
    private static final String TAG_PROTECTED_TICK = "ae2lt.undying_protected_tick";
    private static final float RESTORE_HEALTH = 4.0F;
    private static final int CLEANSING_LIMIT = 3;

    private OverloadArmorUndyingHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFatalDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        float damage = event.getNewDamage();
        if (damage <= 0.0F || damage < player.getHealth() + player.getAbsorptionAmount()) {
            return;
        }
        if (tryTrigger(player, event.getSource(), "fatal_damage")) {
            event.setNewDamage(0.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (tryProtectForcedDeath(player, event.getSource(), "death_event")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (player.dead || player.isDeadOrDying() || player.getHealth() <= 0.0F) {
            tryProtectForcedDeath(player, player.damageSources().genericKill(), "death_tick");
        }
    }

    public static boolean tryProtectForcedDeath(
            ServerPlayer player,
            @Nullable DamageSource source,
            String reason) {
        if (player == null || player.level().isClientSide()) {
            return false;
        }
        if (wasProtectedThisTick(player)) {
            restoreSurvivalState(player);
            return true;
        }
        return tryTrigger(player, source != null ? source : player.damageSources().genericKill(), reason);
    }

    public static boolean wasProtectedThisTick(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }
        return player.getPersistentData().getLong(TAG_PROTECTED_TICK) == player.level().getGameTime();
    }

    private static boolean tryTrigger(ServerPlayer player, DamageSource source, String reason) {
        if (player.isSpectator()) {
            return false;
        }
        long now = player.level().getGameTime();
        for (var active : collectActiveLastStand(player)) {
            if (UndyingSubmodule.getCooldown(active.armor()) > 0) {
                continue;
            }
            int comboIndex = UndyingSubmodule.nextComboIndex(active.armor(), now);
            long cost = scaledCost(active.tuning().feCost(), comboIndex);
            if (!payCost(player, active.armor(), cost)) {
                OverloadArmorState.markEnergyUnpaid(active.armor(), "energy");
                continue;
            }
            UndyingSubmodule.setCooldown(active.armor(), Math.max(1, active.tuning().cooldownTicks()), now);
            UndyingSubmodule.recordTrigger(
                    active.armor(),
                    now,
                    Math.max(1, active.tuning().comboWindowTicks()),
                    comboIndex);
            OverloadArmorState.addPulseLoad(
                    active.armor(),
                    active.submoduleId(),
                    scaledLoad(ArmorOverloadRules.UNDYING_PULSE_LOAD, comboIndex));
            player.getPersistentData().putLong(TAG_PROTECTED_TICK, now);
            restoreSurvivalState(player);
            cleanseHarmfulEffects(player, CLEANSING_LIMIT);
            return true;
        }
        return false;
    }

    private static boolean payCost(ServerPlayer player, ItemStack armor, long cost) {
        if (cost <= 0L) {
            return true;
        }
        ArmorEnergyBuffer.refillFromNetwork(
                armor,
                player,
                Math.max(0L, cost - ArmorEnergyBuffer.read(armor, player.registryAccess())));
        return ArmorEnergyBuffer.tryConsume(armor, player, cost);
    }

    private static void restoreSurvivalState(ServerPlayer player) {
        player.dead = false;
        player.clearFire();
        player.setRemainingFireTicks(0);
        player.resetFallDistance();
        float targetHealth = Math.max(1.0F, Math.min(player.getMaxHealth(), RESTORE_HEALTH));
        if (player.getHealth() < targetHealth) {
            player.setHealth(targetHealth);
        }
        player.invulnerableTime = Math.max(player.invulnerableTime, 20);
        player.hurtTime = 0;
        player.hurtDuration = 0;
    }

    private static int cleanseHarmfulEffects(ServerPlayer player, int maxEffects) {
        int removed = 0;
        for (var effect : List.copyOf(player.getActiveEffects())) {
            if (removed >= maxEffects) {
                break;
            }
            if (effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) {
                continue;
            }
            if (player.removeEffect(effect.getEffect())) {
                removed++;
            }
        }
        return removed;
    }

    private static List<ActiveLastStand> collectActiveLastStand(ServerPlayer player) {
        var out = new ArrayList<ActiveLastStand>();
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET)) {
            ItemStack armor = player.getItemBySlot(slot);
            if (armor.isEmpty() || !(armor.getItem() instanceof BaseOverloadArmorItem)) {
                continue;
            }
            var snapshot = OverloadArmorState.snapshot(player, armor, player.level().registryAccess(), true);
            if (!snapshot.hasCore() || snapshot.locked()) {
                continue;
            }
            for (ItemStack moduleStack : OverloadArmorState.loadModuleStacks(armor, player.level().registryAccess())) {
                if (!(moduleStack.getItem() instanceof OverloadDeviceModuleItem module)
                        || !(moduleStack.getItem() instanceof OverloadArmorSubmoduleItem submoduleProvider)) {
                    continue;
                }
                ItemStack unit = moduleStack.copyWithCount(1);
                submoduleProvider.collectSubmodules(unit, submodule -> {
                    if (submodule == null
                            || !OverloadArmorState.isSubmoduleRuntimeActive(armor, submodule.id())) {
                        return;
                    }
                    for (var capability : module.capabilities(unit)) {
                        if (capability instanceof DeviceCapability.LastStandTuning tuning) {
                            out.add(new ActiveLastStand(armor, submodule.id(), tuning));
                        }
                    }
                });
            }
        }
        return List.copyOf(out);
    }

    private static long scaledCost(long baseCost, int comboIndex) {
        int safeCombo = Math.max(1, comboIndex);
        if (baseCost <= 0L) {
            return 0L;
        }
        if (baseCost > Long.MAX_VALUE / safeCombo) {
            return Long.MAX_VALUE;
        }
        return baseCost * safeCombo;
    }

    private static int scaledLoad(int baseLoad, int comboIndex) {
        long scaled = (long) Math.max(0, baseLoad) * Math.max(1, comboIndex);
        return (int) Math.min(Integer.MAX_VALUE, scaled);
    }

    private record ActiveLastStand(
            ItemStack armor,
            String submoduleId,
            DeviceCapability.LastStandTuning tuning) {
    }
}
