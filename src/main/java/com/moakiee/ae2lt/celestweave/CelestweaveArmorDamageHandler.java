package com.moakiee.ae2lt.celestweave;
import com.moakiee.ae2lt.network.NetworkInit;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.module.ResistanceSubmodule;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector.ActiveCapability;
import com.moakiee.ae2lt.celestweave.service.ArmorEnergyService;
import com.moakiee.ae2lt.celestweave.service.ArmorLightningService;
import com.moakiee.ae2lt.celestweave.service.ArmorModuleLightningPolicy;
import com.moakiee.ae2lt.celestweave.service.ArmorResourceFeedback;
import com.moakiee.ae2lt.network.ShieldHitFeedbackSuppressionPacket;
import com.moakiee.ae2lt.registry.ModDamageTypes;

/**
 * Applies staged mitigation and reflect tuning from active armor modules.
 *
 * <p>Shield payment and mitigation run at the end of Forge's pre-armor hurt event so a fully
 * blocked hit can be canceled before armor, absorption, and final-damage handlers run.
 * {@code reflectPct} bounces pre-overload-shield damage back to LivingEntity attackers.
 * Environmental damage (fire/fall/drown) is never reflected.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class CelestweaveArmorDamageHandler {
    private static final ThreadLocal<Boolean> REFLECTING_DAMAGE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Set<Integer>> SUPPRESSING_SHIELD_HIT_FEEDBACK =
            ThreadLocal.withInitial(HashSet::new);

    private CelestweaveArmorDamageHandler() {}

    // 1.20.1 has no LivingIncomingDamageEvent; LivingHurtEvent is its earliest hook (pre-armor).
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamageTypeImmunity(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        for (var active : ArmorCapabilityCollector.collectPerInstalledStack(player)) {
            if (active.capability() instanceof DeviceCapability.DamageTypeImmunity immunity
                    && event.getSource().is(immunity.damageType())) {
                event.setAmount(0.0F);
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onShieldIncoming(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        float incoming = event.getAmount();
        if (incoming <= 0.0F) {
            return;
        }
        var capabilities = ArmorCapabilityCollector.collectPerInstalledUnit(player);
        ActiveCapability mitigation = collectMitigation(capabilities);
        if (mitigation != null
                && mitigation.capability() instanceof DeviceCapability.StagedMitigation staged) {
            float afterMitigation = ArmorMitigationRules.apply(
                    staged.stage(),
                    classifyDamage(event.getSource()),
                    incoming);
            if (payMitigationLightning(player, mitigation, staged, incoming - afterMitigation)) {
                event.setAmount(afterMitigation);
                if (afterMitigation <= 0.0F) {
                    if (!isReflectingDamage()) {
                        reflectIncomingDamage(player, event.getSource(), incoming);
                    }
                    event.setCanceled(true);
                    return;
                }
                if (!ResistanceSubmodule.isHitFeedbackEnabled(mitigation.armor(), staged.stage())) {
                    markSuppressShieldHitFeedback(player);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPre(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        if (!isReflectingDamage()) {
            reflectIncomingDamage(player, event.getSource(), event.getAmount());
        }
    }

    private static ActiveCapability collectMitigation(java.util.List<ActiveCapability> capabilities) {
        for (var active : capabilities) {
            if (active.capability() instanceof DeviceCapability.StagedMitigation) {
                return active;
            }
        }
        return null;
    }

    private static ArmorMitigationRules.DamageClass classifyDamage(DamageSource source) {
        return ArmorMitigationRules.classify(
                isEnvironmentDamage(source),
                isHardDamage(source));
    }

    private static boolean isHardDamage(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_ARMOR)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypeTags.BYPASSES_EFFECTS)
                || source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                || source.is(DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(DamageTypes.GENERIC_KILL)
                || source.is(DamageTypes.STARVE)
                || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.WITHER)
                || source.is(DamageTypes.WITHER_SKULL)
                || source.is(ModDamageTypes.ELECTROMAGNETIC);
    }

    private static boolean isEnvironmentDamage(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypes.IN_FIRE)
                || source.is(DamageTypes.ON_FIRE)
                || source.is(DamageTypes.LAVA)
                || source.is(DamageTypes.HOT_FLOOR)
                || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.CRAMMING)
                || source.is(DamageTypes.CACTUS)
                || source.is(DamageTypes.DRY_OUT)
                || source.is(DamageTypes.SWEET_BERRY_BUSH)
                || source.is(DamageTypes.FREEZE)
                || source.is(DamageTypes.STALAGMITE);
    }

    private static boolean payMitigationLightning(
            Player player,
            ActiveCapability mitigation,
            DeviceCapability.StagedMitigation staged,
            float preventedDamage) {
        if (!(player instanceof ServerPlayer serverPlayer) || preventedDamage <= 0.0F) {
            return true;
        }
        if ("phase_shield".equals(staged.stage())) {
            return payPhaseShield(serverPlayer, mitigation, preventedDamage);
        }
        long amount = (long) Math.ceil(preventedDamage
                * ArmorModuleLightningPolicy.triggeredCost(ArmorModuleLightningPolicy.Trigger.MATRIX_SHIELD)
                        .highVoltage());
        long feCost = (long) Math.ceil(
                preventedDamage * ArmorOverloadRules.MATRIX_SHIELD_ACTIVE_COST_FE_PER_DAMAGE);
        if (amount <= 0L && feCost <= 0L) {
            return true;
        }
        int comboIndex = ArmorOverloadCombo.nextComboIndex(
                mitigation.armor(),
                ResistanceSubmodule.T1,
                serverPlayer.level().getGameTime());
        long finalAmount = ArmorOverloadCombo.scaledCost(amount, comboIndex);
        ArmorEnergyService.EnergyPayment payment = ArmorEnergyService.consumeActiveCostPayment(
                serverPlayer,
                mitigation.armor(),
                feCost);
        if (!payment.paid()) {
            ArmorResourceFeedback.noFe(serverPlayer);
            return false;
        }
        var lightningCost = ArmorModuleLightningPolicy
                .triggeredCost(ArmorModuleLightningPolicy.Trigger.MATRIX_SHIELD)
                .times(finalAmount);
        if (!ArmorLightningService.consume(serverPlayer, mitigation.armor(), lightningCost)) {
            payment.refund();
            if (lightningCost.extremeHighVoltage() > 0L) {
                ArmorResourceFeedback.noExtremeHighVoltage(serverPlayer);
            } else {
                ArmorResourceFeedback.noHighVoltage(serverPlayer);
            }
            return false;
        }
        ArmorOverloadCombo.recordTrigger(
                mitigation.armor(),
                ResistanceSubmodule.T1,
                serverPlayer.level().getGameTime(),
                AE2LTCommonConfig.overloadArmorShieldComboWindowTicks(),
                comboIndex);
        return true;
    }

    private static boolean payPhaseShield(
            ServerPlayer player,
            ActiveCapability mitigation,
            float preventedDamage) {
        PhaseShieldChargeWindow.Quote quote = PhaseShieldChargeWindow.quote(
                mitigation.armor(),
                player.level().getGameTime(),
                preventedDamage);
        var lightningCost = ArmorLightningService.LightningCost.ehv(quote.ehvCost());
        if (!ArmorLightningService.hasCost(player, mitigation.armor(), lightningCost)) {
            ArmorResourceFeedback.noExtremeHighVoltage(player);
            return false;
        }
        ArmorEnergyService.EnergyPayment payment = ArmorEnergyService.consumeActiveCostPayment(
                player,
                mitigation.armor(),
                quote.feCost());
        if (!payment.paid()) {
            ArmorResourceFeedback.noFe(player);
            return false;
        }
        if (!ArmorLightningService.consume(player, mitigation.armor(), lightningCost)) {
            payment.refund();
            ArmorResourceFeedback.noExtremeHighVoltage(player);
            return false;
        }
        PhaseShieldChargeWindow.record(mitigation.armor(), quote);
        return true;
    }

    public static boolean shouldSuppressShieldHitFeedback(LivingEntity entity) {
        return entity != null && SUPPRESSING_SHIELD_HIT_FEEDBACK.get().contains(entity.getId());
    }

    public static void suppressShieldHitFeedback(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        entity.hurtTime = 0;
        entity.hurtDuration = 0;
    }

    public static void clearSuppressShieldHitFeedback(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        var suppressing = SUPPRESSING_SHIELD_HIT_FEEDBACK.get();
        suppressing.remove(entity.getId());
        if (suppressing.isEmpty()) {
            SUPPRESSING_SHIELD_HIT_FEEDBACK.remove();
        }
    }

    private static void markSuppressShieldHitFeedback(LivingEntity entity) {
        SUPPRESSING_SHIELD_HIT_FEEDBACK.get().add(entity.getId());
        if (entity instanceof ServerPlayer serverPlayer) {
            NetworkInit.sendToPlayer(
                    serverPlayer,
                    new ShieldHitFeedbackSuppressionPacket(serverPlayer.getId()));
        }
    }

    private static void reflectIncomingDamage(Player player, DamageSource source, float incomingDamage) {
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        float reflected = collectReflectedDamage(player, incomingDamage);
        if (reflected > 0.0F) {
            hurtWithReflectGuard(attacker, source, reflected);
        }
    }

    private static boolean isReflectingDamage() {
        return Boolean.TRUE.equals(REFLECTING_DAMAGE.get());
    }

    private static void hurtWithReflectGuard(LivingEntity target, DamageSource source, float damage) {
        boolean wasReflecting = isReflectingDamage();
        REFLECTING_DAMAGE.set(Boolean.TRUE);
        try {
            target.hurt(source, damage);
        } finally {
            REFLECTING_DAMAGE.set(wasReflecting);
        }
    }

    private static float collectReflectedDamage(Player player, float damage) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return 0.0F;
        }
        if (damage <= 0.0F) {
            return 0.0F;
        }
        float reflected = 0.0F;
        for (var active : ArmorCapabilityCollector.collectPerInstalledUnit(player)) {
            if (!(active.capability() instanceof DeviceCapability.ReflectTuning reflect)
                    || reflect.reflectPct() <= 0.0D) {
                continue;
            }
            float remaining = Math.max(0.0F, damage - reflected);
            float amount = Math.min(remaining, damage * (float) reflect.reflectPct());
            if (amount <= 0.0F) {
                continue;
            }
            long cost = (long) Math.ceil(amount * Math.max(0L, reflect.fePerDamage()));
            ArmorEnergyService.EnergyPayment payment = ArmorEnergyService.consumeActiveCostPayment(
                    serverPlayer,
                    active.armor(),
                    cost);
            if (!payment.paid()) {
                ArmorResourceFeedback.noFe(serverPlayer);
                continue;
            }
            reflected += amount;
            if (reflected >= damage) {
                break;
            }
        }
        return reflected;
    }

}
