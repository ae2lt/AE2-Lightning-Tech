package com.moakiee.ae2lt.celestweave;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.FluidTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.module.PhaseFlightSubmodule;
import com.moakiee.ae2lt.celestweave.module.SaturationSubmodule;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector.ActiveCapability;
import com.moakiee.ae2lt.celestweave.service.ArmorInteractionRangeService;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class CelestweaveArmorUtilityHandler {
    private CelestweaveArmorUtilityHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var capabilities = ArmorCapabilityCollector.collectPerInstalledStack(player);
        ArmorInteractionRangeService.tick(player, capabilities);
        tickPurification(player, capabilities);
        tickFoodSustain(player, capabilities);
        if (hasActivePhaseFlight(capabilities)) {
            PhaseFlightSubmodule.applyTransientPhaseState(player);
        } else if (PhaseFlightSubmodule.hasTransientPhaseState(player)) {
            ItemStack escapeArmor = findPhaseFlightArmor(player);
            if (!PhaseFlightSubmodule.tickEscapePhase(player, escapeArmor)) {
                PhaseFlightSubmodule.clearTransientPhaseState(player);
            }
        }
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

    private static boolean hasActivePhaseFlight(List<ActiveCapability> capabilities) {
        for (var active : capabilities) {
            if (active.capability() instanceof DeviceCapability.FlightMode flight
                    && flight.kind() == com.moakiee.ae2lt.device.capability.FlightKind.PHASE) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack findPhaseFlightArmor(Player player) {
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET)) {
            ItemStack armor = player.getItemBySlot(slot);
            if (armor.isEmpty() || !(armor.getItem() instanceof BaseCelestweaveArmorItem)) {
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

    private static void clearPlayerRuntime(Player player) {
        ArmorCapabilityCollector.clearCache(player);
        PhaseFlightSubmodule.clearTransientPhaseState(player);
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET)) {
            ItemStack armor = player.getItemBySlot(slot);
            if (!armor.isEmpty() && armor.getItem() instanceof BaseCelestweaveArmorItem) {
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
