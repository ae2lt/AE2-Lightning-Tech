package com.moakiee.ae2lt.overload.armor;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.tags.FluidTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.overload.armor.module.AutoFeedSubmodule;
import com.moakiee.ae2lt.overload.armor.module.PhaseFlightSubmodule;
import com.moakiee.ae2lt.overload.armor.service.ArmorCapabilityCollector;
import com.moakiee.ae2lt.overload.armor.service.ArmorCapabilityCollector.ActiveCapability;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class OverloadArmorUtilityHandler {
    private static final String DIG_PULSE_TICK_TAG = "ae2lt.dig_affinity_pulse_tick";

    private OverloadArmorUtilityHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var capabilities = ArmorCapabilityCollector.collectPerInstalledStack(player);
        tickCleanse(player, capabilities);
        tickAutoFeed(player, capabilities);
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
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        var capabilities = ArmorCapabilityCollector.collectPerInstalledStack(serverPlayer);
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
        double multiplier = ArmorDynamicLoadRules.digSpeedMultiplier(
                underwater,
                airborne,
                underwaterMultiplier,
                airborneMultiplier);
        if (multiplier <= 1.0D) {
            return;
        }

        event.setNewSpeed((float) (event.getNewSpeed() * multiplier));
        pulseDigLoadOncePerTick(serverPlayer, pulseSource.armor());
    }

    private static void tickCleanse(ServerPlayer player, List<ActiveCapability> capabilities) {
        for (var active : capabilities) {
            if (!(active.capability() instanceof DeviceCapability.CleanseTuning cleanse)) {
                continue;
            }
            int period = Math.max(1, cleanse.periodTicks());
            if (player.tickCount % period != 0) {
                continue;
            }
            int removed = cleanseHarmfulEffects(player, Math.max(1, cleanse.strength()));
            int load = ArmorDynamicLoadRules.cleansePulseLoad(
                    removed,
                    AE2LTCommonConfig.overloadArmorCleanseLoadPerEffect());
            OverloadArmorState.addPulseLoad(active.armor(), active.submoduleId(), load);
        }
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

    private static void tickAutoFeed(ServerPlayer player, List<ActiveCapability> capabilities) {
        for (var active : capabilities) {
            if (!(active.capability() instanceof DeviceCapability.AutoFeed autoFeed)) {
                continue;
            }
            if (AutoFeedSubmodule.getCooldown(active.armor()) > 0) {
                continue;
            }
            int threshold = Math.clamp(autoFeed.hungerThreshold(), 0, 20);
            if (player.getFoodData().getFoodLevel() > threshold || !player.canEat(false)) {
                continue;
            }
            if (consumeFoodFromInventory(player)) {
                AutoFeedSubmodule.setCooldown(
                        active.armor(),
                        AE2LTCommonConfig.overloadArmorAutoFeedCooldownTicks());
                OverloadArmorState.addPulseLoad(
                        active.armor(),
                        active.submoduleId(),
                        AE2LTCommonConfig.overloadArmorAutoFeedPulseLoad());
                return;
            }
        }
    }

    private static boolean consumeFoodFromInventory(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null || !player.canEat(food.canAlwaysEat())) {
                continue;
            }
            ItemStack result = player.eat(player.level(), stack, food);
            inventory.setItem(slot, result);
            inventory.setChanged();
            return true;
        }
        return false;
    }

    private static void pulseDigLoadOncePerTick(ServerPlayer player, ItemStack armor) {
        long now = player.level().getGameTime();
        var data = player.getPersistentData();
        if (data.getLong(DIG_PULSE_TICK_TAG) == now) {
            return;
        }
        data.putLong(DIG_PULSE_TICK_TAG, now);
        OverloadArmorState.addPulseLoad(
                armor,
                "dig_affinity",
                AE2LTCommonConfig.overloadArmorDigPulseLoad());
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
            if (armor.isEmpty() || !(armor.getItem() instanceof BaseOverloadArmorItem)) {
                continue;
            }
            if (OverloadArmorState.isSubmoduleInstalled(armor, PhaseFlightSubmodule.INSTANCE.id())) {
                return armor;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void clearPlayerRuntime(Player player) {
        PhaseFlightSubmodule.clearTransientPhaseState(player);
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET)) {
            ItemStack armor = player.getItemBySlot(slot);
            if (!armor.isEmpty() && armor.getItem() instanceof BaseOverloadArmorItem) {
                OverloadArmorState.clearTransientRuntimeAndCaches(armor);
            }
        }
    }

}
