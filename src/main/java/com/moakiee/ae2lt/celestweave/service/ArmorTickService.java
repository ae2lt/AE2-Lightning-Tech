package com.moakiee.ae2lt.celestweave.service;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;

public final class ArmorTickService {
    private ArmorTickService() {
    }

    public static void tickEquipped(
            Player player,
            ItemStack armor,
            boolean equipped,
            HolderLookup.Provider registries,
            Dist dist) {
        CelestweaveArmorState.ensureArmorId(armor);
        var installedSubmodules = CelestweaveArmorState.collectInstalledSubmoduleEntries(armor, registries);
        if (!equipped) {
            CelestweaveArmorState.syncSubmoduleActiveState(player, armor, installedSubmodules, false, dist);
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            ArmorEnergyService.refillFromBoundNetworkIfLow(serverPlayer, armor, registries);
            if (!ArmorEnergyService.consumePassiveDrain(serverPlayer, armor, installedSubmodules, registries)) {
                // Publish the forced-off state to the client so derived activity (e.g. dig affinity
                // in BreakSpeed) matches the server instead of staying on with no power.
                CelestweaveArmorState.setModulesPowered(armor, false);
                CelestweaveArmorState.syncSubmoduleActiveState(player, armor, installedSubmodules, false, dist);
                CelestweaveArmorState.tickEquipped(player, armor, installedSubmodules, registries);
                return;
            }
            CelestweaveArmorState.setModulesPowered(armor, true);
        }

        CelestweaveArmorState.syncSubmoduleActiveState(player, armor, installedSubmodules, true, dist);
        CelestweaveArmorState.tickActiveSubmodules(player, armor, installedSubmodules, dist);
        CelestweaveArmorState.tickEquipped(player, armor, installedSubmodules, registries);
    }
}
