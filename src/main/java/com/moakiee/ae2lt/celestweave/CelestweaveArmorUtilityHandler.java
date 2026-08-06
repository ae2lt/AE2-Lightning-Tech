package com.moakiee.ae2lt.celestweave;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.FluidTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.module.PhaseFlightSubmodule;
import com.moakiee.ae2lt.celestweave.module.SaturationSubmodule;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector.ActiveCapability;
import com.moakiee.ae2lt.celestweave.service.ArmorInteractionRangeService;
import com.moakiee.ae2lt.celestweave.service.ArmorMovementAssistService;
import com.moakiee.ae2lt.celestweave.phase.CelestweaveEquipmentAccess;
import com.moakiee.ae2lt.celestweave.phase.PhaseLockService;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class CelestweaveArmorUtilityHandler {
    private CelestweaveArmorUtilityHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PhaseLockService.tick(player);
        var capabilities = ArmorCapabilityCollector.collectPerInstalledStack(player);
        extinguishShieldedPlayer(player, capabilities);
        ArmorInteractionRangeService.tick(player, capabilities);
        ArmorMovementAssistService.tick(player, capabilities);
        tickPurification(player, capabilities);
        tickFoodSustain(player, capabilities);
        ActiveCapability phaseTraversal = findActivePhaseTraversal(capabilities);
        if (phaseTraversal != null
                && PhaseFlightSubmodule.shouldUsePhaseTraversal(player, phaseTraversal.armor())) {
            PhaseFlightSubmodule.applyTransientPhaseState(player);
        } else if (phaseTraversal == null && PhaseFlightSubmodule.hasTransientPhaseState(player)) {
            ItemStack escapeArmor = findPhaseFlightArmor(player);
            if (!PhaseFlightSubmodule.tickEscapePhase(player, escapeArmor)) {
                PhaseFlightSubmodule.clearTransientPhaseState(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack removedArmor = event.getFrom();
        if (removedArmor.isEmpty() || !(removedArmor.getItem() instanceof BaseCelestweaveArmorItem)) {
            return;
        }
        if (!PhaseFlightArmorRemovalRules.shouldDeactivateRemovedArmor(
                CelestweaveArmorState.getArmorId(removedArmor),
                celestweaveArmorId(event.getTo()))) {
            return;
        }
        deactivateRemovedArmor(player, removedArmor);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayerRuntime(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        clearPlayerRuntime(event.getOriginal());
        clearPlayerRuntime(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        forceResyncEquippedArmor(player);
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        var capabilities = ArmorCapabilityCollector.collectPerInstalledStack(player);
        double underwaterMultiplier = 1.0D;
        double airborneMultiplier = 1.0D;
        ActiveCapability pulseSource = null;
        for (var active : capabilities) {
            if (!(active.capability() instanceof DeviceCapability.DigAffinity dig)) {
                continue;
            }
            if ("underwater".equals(dig.env())) {
                underwaterMultiplier = Math.max(underwaterMultiplier, dig.speedMul());
            } else if ("airborne".equals(dig.env())) {
                airborneMultiplier = Math.max(airborneMultiplier, dig.speedMul());
            }
            pulseSource = active;
        }
        if (pulseSource == null) {
            return;
        }

        boolean underwater = player.isEyeInFluid(FluidTags.WATER) || player.isUnderWater();
        boolean airborne = !player.onGround();
        double multiplier = digSpeedMultiplier(
                underwater,
                airborne,
                underwaterMultiplier,
                airborneMultiplier);
        if (multiplier <= 1.0D) {
            return;
        }

        event.setNewSpeed((float) (event.getNewSpeed() * multiplier));
    }

    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !PurificationEffectRules.canPurify(event.getEffectInstance())) {
            return;
        }
        for (var active : ArmorCapabilityCollector.collectPerInstalledStack(player)) {
            if (active.capability() instanceof DeviceCapability.PurificationTuning) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                return;
            }
        }
    }

    private static void tickPurification(ServerPlayer player, List<ActiveCapability> capabilities) {
        for (var active : capabilities) {
            if (!(active.capability() instanceof DeviceCapability.PurificationTuning purification)) {
                continue;
            }
            int period = Math.max(1, purification.periodTicks());
            if (player.tickCount % period != 0) {
                continue;
            }
            int limit = Math.max(1, purification.strength());
            purifyEffects(player, limit);
        }
    }

    private static int purifyEffects(ServerPlayer player, int maxEffects) {
        int removed = 0;
        for (var effect : List.copyOf(player.getActiveEffects())) {
            if (removed >= maxEffects) {
                break;
            }
            if (!PurificationEffectRules.canPurify(effect)) {
                continue;
            }
            if (player.removeEffect(effect.getEffect())) {
                removed++;
            }
        }
        return removed;
    }

    private static void tickFoodSustain(ServerPlayer player, List<ActiveCapability> capabilities) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        for (var active : capabilities) {
            if (!(active.capability() instanceof DeviceCapability.FoodSustain foodSustain)) {
                continue;
            }
            if (SaturationSubmodule.getCooldown(active.armor(), player) > 0) {
                continue;
            }
            int intervalTicks = Math.max(1, foodSustain.checkIntervalTicks());
            int targetFood = Math.clamp(foodSustain.targetFood(), 0, 20);
            float targetSaturation = Math.clamp(foodSustain.targetSaturation(), 0.0F, 20.0F);
            FoodData foodData = player.getFoodData();
            int currentFood = foodData.getFoodLevel();
            float currentSaturation = foodData.getSaturationLevel();
            if (currentFood >= targetFood && currentSaturation >= targetSaturation) {
                SaturationSubmodule.setCooldown(active.armor(), player, intervalTicks);
                return;
            }
            if (currentFood < targetFood) {
                foodData.setFoodLevel(Math.max(currentFood, targetFood));
            }
            if (currentSaturation < targetSaturation) {
                foodData.setSaturation(Math.max(currentSaturation, targetSaturation));
            }
            SaturationSubmodule.setCooldown(active.armor(), player, intervalTicks);
            return;
        }
    }

    private static void extinguishShieldedPlayer(
            ServerPlayer player,
            List<ActiveCapability> capabilities) {
        if (!player.isOnFire()) {
            return;
        }
        for (var active : capabilities) {
            if (active.capability() instanceof DeviceCapability.StagedMitigation) {
                player.clearFire();
                player.setRemainingFireTicks(0);
                return;
            }
        }
    }

    @Nullable
    private static ActiveCapability findActivePhaseTraversal(List<ActiveCapability> capabilities) {
        for (var active : capabilities) {
            if (active.capability() instanceof DeviceCapability.PhaseTraversal) {
                return active;
            }
        }
        return null;
    }

    private static ItemStack findPhaseFlightArmor(Player player) {
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET)) {
            ItemStack armor = CelestweaveEquipmentAccess.findArmor(player, slot);
            if (armor.isEmpty()) {
                continue;
            }
            if (CelestweaveArmorState.isSubmoduleInstalled(
                    armor,
                    player.registryAccess(),
                    PhaseFlightSubmodule.INSTANCE.id())) {
                return armor;
            }
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    private static UUID celestweaveArmorId(ItemStack armor) {
        if (armor.isEmpty() || !(armor.getItem() instanceof BaseCelestweaveArmorItem)) {
            return null;
        }
        return CelestweaveArmorState.getArmorId(armor);
    }

    private static void deactivateRemovedArmor(ServerPlayer player, ItemStack armor) {
        ArmorCapabilityCollector.clearCache(player);
        CelestweaveArmorState.syncSubmoduleActiveState(
                player,
                armor,
                player.registryAccess(),
                false,
                Dist.DEDICATED_SERVER);
    }

    private static void forceResyncEquippedArmor(ServerPlayer player) {
        ArmorCapabilityCollector.clearCache(player);
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET)) {
            ItemStack armor = CelestweaveEquipmentAccess.findArmor(player, slot);
            if (armor.isEmpty()) {
                continue;
            }
            // Re-assert the authoritative phase-flight push after a dimension change; every other
            // submodule's active state the client derives from the (already synced) stack.
            CelestweaveArmorState.syncSubmoduleActiveState(
                    player,
                    armor,
                    player.registryAccess(),
                    true,
                    Dist.DEDICATED_SERVER,
                    true);
        }
    }

    private static void clearPlayerRuntime(Player player) {
        ArmorCapabilityCollector.clearCache(player);
        PhaseFlightMovementGuard.clear(player);
        PhaseFlightSubmodule.clearTransientPhaseState(player);
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET)) {
            ItemStack armor = CelestweaveEquipmentAccess.findArmor(player, slot);
            if (!armor.isEmpty()) {
                CelestweaveArmorState.clearTransientRuntimeAndCaches(armor);
            }
        }
    }

    private static double digSpeedMultiplier(
            boolean underwater,
            boolean airborne,
            double underwaterMultiplier,
            double airborneMultiplier) {
        double multiplier = 1.0D;
        if (underwater) {
            multiplier *= Math.max(1.0D, underwaterMultiplier);
        }
        if (airborne) {
            multiplier *= Math.max(1.0D, airborneMultiplier);
        }
        return multiplier;
    }

}
