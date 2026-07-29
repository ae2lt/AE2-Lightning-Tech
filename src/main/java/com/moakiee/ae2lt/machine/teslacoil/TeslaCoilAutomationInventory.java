package com.moakiee.ae2lt.machine.teslacoil;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.machine.common.MatrixHidingAutomationInventory;

public class TeslaCoilAutomationInventory
        extends MatrixHidingAutomationInventory<TeslaCoilInventory> {
    public TeslaCoilAutomationInventory(TeslaCoilInventory inventory) {
        super(inventory, TeslaCoilInventory.SLOT_MATRIX);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        Objects.requireNonNull(stack, "stack");
        if (slot < 0 || slot >= inventory.getSlots()) {
            throw new IllegalArgumentException("Slot " + slot + " not in valid range");
        }

        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (slot == TeslaCoilInventory.SLOT_MATRIX) {
            return stack;
        }

        if (!inventory.isItemValid(slot, stack)) {
            return stack;
        }

        return inventory.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= inventory.getSlots()) {
            throw new IllegalArgumentException("Slot " + slot + " not in valid range");
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot < 0 || slot >= inventory.getSlots()) {
            throw new IllegalArgumentException("Slot " + slot + " not in valid range");
        }
        if (stack.isEmpty()) {
            return false;
        }
        if (slot == TeslaCoilInventory.SLOT_MATRIX) {
            return false;
        }
        return inventory.isItemValid(slot, stack);
    }
}
