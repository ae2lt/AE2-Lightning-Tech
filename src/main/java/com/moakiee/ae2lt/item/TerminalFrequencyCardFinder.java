package com.moakiee.ae2lt.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.upgrades.IUpgradeableItem;

/**
 * Finds overloaded frequency cards installed as upgrades inside wireless
 * terminals the player is carrying (hotbar/inventory/offhand) or wearing in a
 * Curios slot.
 *
 * <p>This uses only AE2's {@link IUpgradeableItem} contract, so it does not
 * reference ae2wtlib directly: the frequency card can only be installed into a
 * terminal's upgrade inventory when ae2wtlib registered it there, so any
 * upgradable item that contains the card is, by construction, such a terminal.</p>
 *
 * <p>The returned stacks are read-only snapshots intended for the auto-connect
 * path (which only reads the card's frequency id and auto-connect flag). Writes
 * to a terminal-installed card must go through the terminal's
 * {@link appeng.api.upgrades.IUpgradeInventory} so they persist; see the
 * frequency card menu's select/toggle packets.</p>
 */
public final class TerminalFrequencyCardFinder {

    private TerminalFrequencyCardFinder() {
    }

    public static List<ItemStack> findFrequencyCards(Player player) {
        List<ItemStack> result = new ArrayList<>();

        var inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            collectFromTerminal(stack, result);
        }
        for (ItemStack stack : inventory.offhand) {
            collectFromTerminal(stack, result);
        }
        for (ItemStack stack : CuriosFrequencyCardFinder.findAllEquippedStacks(player)) {
            collectFromTerminal(stack, result);
        }

        return result;
    }

    private static void collectFromTerminal(ItemStack terminalStack, List<ItemStack> out) {
        if (terminalStack.isEmpty() || !(terminalStack.getItem() instanceof IUpgradeableItem upgradeable)) {
            return;
        }
        var upgrades = upgradeable.getUpgrades(terminalStack);
        for (int slot = 0; slot < upgrades.size(); slot++) {
            ItemStack card = upgrades.getStackInSlot(slot);
            if (card.getItem() instanceof OverloadedFrequencyCardItem) {
                out.add(card);
            }
        }
    }
}
