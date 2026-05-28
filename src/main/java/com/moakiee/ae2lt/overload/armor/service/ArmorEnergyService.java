package com.moakiee.ae2lt.overload.armor.service;

import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.overload.armor.ArmorEnergyBuffer;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem;

public final class ArmorEnergyService {
    private ArmorEnergyService() {
    }

    public static long refillFromBoundNetwork(Player player, ItemStack armor, HolderLookup.Provider registries) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return 0L;
        }
        return ArmorEnergyBuffer.refillFromNetwork(armor, serverPlayer, Long.MAX_VALUE);
    }

    public static boolean consumePassiveDrain(Player player, ItemStack armor, HolderLookup.Provider registries) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        return consumePassiveDrain(serverPlayer, armor, computePassiveDrain(serverPlayer, armor, registries), "energy");
    }

    public static boolean consumePassiveDrain(Player player, ItemStack armor, long amount, String reason) {
        return consumeCost(player, armor, amount, reason);
    }

    public static boolean consumeActiveCost(Player player, ItemStack armor, long amount, String reason) {
        return consumeCost(player, armor, amount, reason);
    }

    private static boolean consumeCost(Player player, ItemStack armor, long amount, String reason) {
        if (amount <= 0L) {
            return true;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        ArmorEnergyBuffer.refillFromNetwork(
                armor,
                serverPlayer,
                Math.max(0L, amount - ArmorEnergyBuffer.read(armor, serverPlayer.registryAccess())));
        boolean paid = ArmorEnergyBuffer.tryConsume(armor, serverPlayer, amount);
        if (!paid) {
            OverloadArmorState.markEnergyUnpaid(armor, reason);
        }
        return paid;
    }

    private static long computePassiveDrain(ServerPlayer player, ItemStack armor, HolderLookup.Provider registries) {
        long drain = 0L;
        double multiplier = 1.0D;
        for (ItemStack module : OverloadArmorState.loadModuleStacks(armor, registries)) {
            if (!(module.getItem() instanceof OverloadDeviceModuleItem provider)) {
                continue;
            }
            if (!moduleRuntimeActive(armor, module)) {
                continue;
            }
            List<DeviceCapability> capabilities = provider.capabilities(module);
            boolean movingFlight = hasFlightMode(capabilities) && isMovingInFlight(player);
            for (DeviceCapability capability : capabilities) {
                if (capability instanceof DeviceCapability.PassiveDrain passiveDrain) {
                    long fePerTick = Math.max(0L, passiveDrain.fePerTick());
                    if (movingFlight) {
                        fePerTick = Math.max(fePerTick, ArmorOverloadRules.FLIGHT_MOVING_DRAIN_FE);
                    }
                    drain += fePerTick * Math.max(1, module.getCount());
                } else if (capability instanceof DeviceCapability.EnergyEfficiency efficiency) {
                    multiplier *= Math.max(0.0D, efficiency.drainMul());
                }
            }
        }
        return (long) Math.ceil(drain * multiplier);
    }

    private static boolean moduleRuntimeActive(ItemStack armor, ItemStack module) {
        if (!(module.getItem() instanceof OverloadArmorSubmoduleItem provider)) {
            return false;
        }
        boolean[] active = {false};
        provider.collectSubmodules(module, submodule -> {
            if (submodule != null && OverloadArmorState.isSubmoduleRuntimeActive(armor, submodule.id())) {
                active[0] = true;
            }
        });
        return active[0];
    }

    private static boolean hasFlightMode(List<DeviceCapability> capabilities) {
        for (DeviceCapability capability : capabilities) {
            if (capability instanceof DeviceCapability.FlightMode) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMovingInFlight(ServerPlayer player) {
        if (!player.getAbilities().flying) {
            return false;
        }
        Vec3 motion = player.getDeltaMovement();
        return motion.lengthSqr() > 1.0E-4D;
    }
}
