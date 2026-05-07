package com.moakiee.ae2lt.machine.overloadfactory;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

public class OverloadProcessingFactoryAutomationInventory implements IItemHandlerModifiable {
    private final OverloadProcessingFactoryInventory inventory;

    public OverloadProcessingFactoryAutomationInventory(OverloadProcessingFactoryInventory inventory) {
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
    public int getSlotLimit(int slot) {
        return inventory.getSlotLimit(slot);
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

