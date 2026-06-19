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
        CelestweaveArmorState.syncSubmoduleActiveState(player, armor, installedSubmodules, equipped, dist);
        if (!equipped) {
            // sync(equipped=false) above already drops this armor's active entries from the runtime maps.
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            ArmorEnergyService.refillFromBoundNetworkIfLow(serverPlayer, armor, registries);
            if (!ArmorEnergyService.consumePassiveDrain(serverPlayer, armor, registries)) {
                CelestweaveArmorState.syncSubmoduleActiveState(player, armor, installedSubmodules, false, dist);
                CelestweaveArmorState.tickEquipped(player, armor, installedSubmodules, registries);
                return;
            }
        }

        CelestweaveArmorState.tickActiveSubmodules(player, armor, installedSubmodules, dist);
        CelestweaveArmorState.tickEquipped(player, armor, installedSubmodules, registries);
    }
}
