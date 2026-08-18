package com.moakiee.ae2lt.integration.ae2wtlib;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.networking.IGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;

import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;

import de.mari_023.ae2wtlib.wut.WUTHandler;

/** Shared resolver for AE2WTLib terminals using an overloaded frequency card. */
public final class WirelessTerminalFrequencyLink {
    private WirelessTerminalFrequencyLink() {
    }

    /**
     * Resolves the card directly from a terminal stack before its menu host exists. The slot
     * count must match {@link de.mari_023.ae2wtlib.terminal.WTMenuHost}; using the vanilla
     * wireless-terminal item's two-slot view would miss cards stored in later WUT slots.
     */
    @Nullable
    public static IGridNode resolve(Player player, ItemStack terminalStack) {
        if (terminalStack.isEmpty()) {
            return null;
        }
        return resolve(player,
                UpgradeInventories.forItem(terminalStack, WUTHandler.getUpgradeCardCount()));
    }

    /** Resolves the advanced transmitter targeted by the first bound frequency card. */
    @Nullable
    public static IGridNode resolve(Player player, IUpgradeInventory upgrades) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        int frequencyId = findBoundFrequency(upgrades);
        if (frequencyId <= 0) {
            return null;
        }
        var manager = WirelessFrequencyManager.get();
        return manager == null
                ? null
                : manager.resolveAdvancedNode(frequencyId, serverLevel.getServer());
    }

    /** A resolved frequency route is usable only while its target ME network is powered. */
    public static boolean isNetworkPowered(@Nullable IGridNode node) {
        return node != null && node.isPowered();
    }

    private static int findBoundFrequency(IUpgradeInventory upgrades) {
        for (int slot = 0; slot < upgrades.size(); slot++) {
            var stack = upgrades.getStackInSlot(slot);
            if (stack.getItem() instanceof OverloadedFrequencyCardItem) {
                var data = OverloadedFrequencyCardItem.getData(stack);
                if (data.isBound()) {
                    return data.frequencyId();
                }
            }
        }
        return -1;
    }
}
