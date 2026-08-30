package com.moakiee.ae2lt.item;

import java.util.function.UnaryOperator;

import net.minecraft.world.item.ItemStack;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableItem;

/**
 * Helpers for reading and writing the overloaded frequency card that is
 * installed as an upgrade inside a wireless terminal item stack.
 *
 * <p>Writes go through the terminal's {@link appeng.api.upgrades.IUpgradeInventory}
 * (via {@code setItemDirect}), which AE2 persists immediately into the terminal
 * stack's NBT. Mutating a stack returned by {@code getStackInSlot}
 * in place would be lost, so callers must use {@link #updateCard}.</p>
 */
public final class TerminalCardAccess {

    private TerminalCardAccess() {
    }

    /**
     * @return the data of the frequency card installed in the terminal, or an
     *         empty/unbound data record when none is present.
     */
    public static OverloadedFrequencyCardData readCardData(ItemStack terminalStack) {
        ItemStack card = findCard(terminalStack);
        return card.isEmpty()
                ? OverloadedFrequencyCardData.empty()
                : OverloadedFrequencyCardItem.getData(card);
    }

    /**
     * Reads the card from an already-open terminal host's authoritative upgrade
     * inventory. Menu actions should prefer this overload so their changes are
     * visible to the menu slots and are synchronized back to the client.
     */
    public static OverloadedFrequencyCardData readCardData(IUpgradeInventory upgrades) {
        ItemStack card = findCard(upgrades);
        return card.isEmpty()
                ? OverloadedFrequencyCardData.empty()
                : OverloadedFrequencyCardItem.getData(card);
    }

    public static boolean hasCard(ItemStack terminalStack) {
        return !findCard(terminalStack).isEmpty();
    }

    public static boolean hasCard(IUpgradeInventory upgrades) {
        return !findCard(upgrades).isEmpty();
    }

    /**
     * @return a snapshot of the installed frequency card stack, or
     *         {@link ItemStack#EMPTY} if the terminal has none.
     */
    public static ItemStack findCard(ItemStack terminalStack) {
        if (terminalStack.isEmpty() || !(terminalStack.getItem() instanceof IUpgradeableItem upgradeable)) {
            return ItemStack.EMPTY;
        }
        return findCard(upgradeable.getUpgrades(terminalStack));
    }

    /**
     * Finds the frequency card in an existing terminal upgrade inventory.
     */
    public static ItemStack findCard(IUpgradeInventory upgrades) {
        for (int slot = 0; slot < upgrades.size(); slot++) {
            ItemStack card = upgrades.getStackInSlot(slot);
            if (card.getItem() instanceof OverloadedFrequencyCardItem) {
                return card;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Applies a mutation to the installed frequency card's data and persists it
     * back to the terminal stack.
     *
     * @return {@code true} if a card was found and updated.
     */
    public static boolean updateCard(ItemStack terminalStack, UnaryOperator<OverloadedFrequencyCardData> mutation) {
        if (terminalStack.isEmpty() || !(terminalStack.getItem() instanceof IUpgradeableItem upgradeable)) {
            return false;
        }
        return updateCard(upgradeable.getUpgrades(terminalStack), mutation);
    }

    /**
     * Updates a card through an already-open terminal host's upgrade inventory.
     * This is the menu-safe path: {@code setItemDirect} both persists the outer
     * terminal stack and marks the inventory used by the menu slots as changed.
     */
    public static boolean updateCard(
            IUpgradeInventory upgrades,
            UnaryOperator<OverloadedFrequencyCardData> mutation) {
        for (int slot = 0; slot < upgrades.size(); slot++) {
            ItemStack card = upgrades.getStackInSlot(slot);
            if (card.getItem() instanceof OverloadedFrequencyCardItem) {
                ItemStack updated = card.copy();
                OverloadedFrequencyCardItem.setData(
                        updated,
                        mutation.apply(OverloadedFrequencyCardItem.getData(updated)));
                upgrades.setItemDirect(slot, updated);
                return true;
            }
        }
        return false;
    }
}
