package com.moakiee.ae2lt.machine.overloadfactory;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.machine.common.MatrixHidingAutomationInventory;

public class OverloadProcessingFactoryAutomationInventory
        extends MatrixHidingAutomationInventory<OverloadProcessingFactoryInventory> {
    public OverloadProcessingFactoryAutomationInventory(OverloadProcessingFactoryInventory inventory) {
        super(inventory, OverloadProcessingFactoryInventory.SLOT_MATRIX);
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
        if (inventory.isOutputSlot(slot) || slot == OverloadProcessingFactoryInventory.SLOT_MATRIX) {
            return stack;
        }

        if (inventory.isInputSlot(slot)) {
            return inventory.insertItem(slot, stack, simulate);
        }

        return stack;
    }

    public ItemStack insertItem(ItemStack stack, boolean simulate) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = stack;
        for (int slot = OverloadProcessingFactoryInventory.SLOT_INPUT_0;
             slot <= OverloadProcessingFactoryInventory.SLOT_INPUT_8;
             slot++) {
            remainder = inventory.insertItem(slot, remainder, simulate);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!inventory.isOutputSlot(slot)) {
            if (slot < 0 || slot >= inventory.getSlots()) {
                throw new IllegalArgumentException("Slot " + slot + " not in valid range");
            }
            return ItemStack.EMPTY;
        }
        return inventory.extractItem(slot, amount, simulate);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot < 0 || slot >= inventory.getSlots()) {
            throw new IllegalArgumentException("Slot " + slot + " not in valid range");
        }
        if (stack.isEmpty()) {
            return false;
        }
        return slot != OverloadProcessingFactoryInventory.SLOT_MATRIX
                && inventory.isInputSlot(slot);
    }
}
