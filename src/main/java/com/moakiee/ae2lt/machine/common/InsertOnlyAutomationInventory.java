package com.moakiee.ae2lt.machine.common;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Capability-facing item wrapper for machines whose exposed slots are inputs.
 */
public class InsertOnlyAutomationInventory implements IItemHandlerModifiable {
    private final IItemHandlerModifiable inventory;

    public InsertOnlyAutomationInventory(IItemHandlerModifiable inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public int getSlots() {
        return inventory.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        inventory.setStackInSlot(slot, stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return inventory.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        validateSlotIndex(slot);
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return inventory.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return inventory.isItemValid(slot, stack);
    }

    private void validateSlotIndex(int slot) {
        if (slot < 0 || slot >= inventory.getSlots()) {
            throw new IllegalArgumentException(
                    "Slot " + slot + " not in valid range - [0," + inventory.getSlots() + ")");
        }
    }
}
