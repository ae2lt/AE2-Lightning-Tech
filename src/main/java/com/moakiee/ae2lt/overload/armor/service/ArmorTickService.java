package com.moakiee.ae2lt.overload.armor.service;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.overload.armor.ArmorDynamicLoadRules;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;

public final class ArmorTickService {
    private ArmorTickService() {
    }

    public static void tickEquipped(
            Player player,
            ItemStack armor,
            boolean equipped,
            HolderLookup.Provider registries,
            Dist dist) {
        OverloadArmorState.ensureArmorId(armor);
        OverloadArmorState.syncSubmoduleActiveState(player, armor, registries, equipped, dist);
        if (!equipped) {
            OverloadArmorState.clearTransientRuntime(armor);
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            ArmorEnergyService.refillFromBoundNetwork(serverPlayer, armor, registries);
            if (!ArmorEnergyService.consumePassiveDrain(serverPlayer, armor, registries)) {
                OverloadArmorState.syncSubmoduleActiveState(player, armor, registries, false, dist);
                OverloadArmorState.tickEquipped(player, armor, registries);
                return;
            }
        }

        OverloadArmorState.tickActiveSubmodules(player, armor, registries, dist);
        var snapshot = OverloadArmorState.tickEquipped(player, armor, registries);
        if (player instanceof ServerPlayer serverPlayer) {
            long demand = ArmorDynamicLoadRules.overloadDemand(
                    snapshot.currentLoad(),
                    snapshot.baseOverload(),
                    AE2LTCommonConfig.overloadArmorCurveExponent(),
                    AE2LTCommonConfig.overloadArmorPowerDemandScale());
            ArmorEnergyService.consumeActiveCost(serverPlayer, armor, demand, "energy");
        }
    }
}
