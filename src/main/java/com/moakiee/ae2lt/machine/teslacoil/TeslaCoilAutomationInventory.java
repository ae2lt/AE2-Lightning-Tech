package com.moakiee.ae2lt.machine.teslacoil;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

public class TeslaCoilAutomationInventory implements IItemHandlerModifiable {
    private final TeslaCoilInventory inventory;

    public TeslaCoilAutomationInventory(TeslaCoilInventory inventory) {
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
        if (slot == TeslaCoilInventory.SLOT_MATRIX) {
            return false;
        }
        return inventory.isItemValid(slot, stack);
    }
}

