package com.moakiee.ae2lt.machine.crystalcatalyzer;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Capability-facing inventory wrapper.
 *
 * <p>Automation may insert catalysts into slot 0. The dedicated matrix slot is
 * reserved for manual GUI placement, and extraction is restricted to output.</p>
 */
public class CrystalCatalyzerAutomationInventory implements IItemHandlerModifiable {
    private final CrystalCatalyzerInventory inventory;

    public CrystalCatalyzerAutomationInventory(CrystalCatalyzerInventory inventory) {
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

        if (slot == CrystalCatalyzerInventory.SLOT_MATRIX
                || slot == CrystalCatalyzerInventory.SLOT_OUTPUT) {
            return stack;
        }

        if (slot == CrystalCatalyzerInventory.SLOT_CATALYST
                && inventory.isItemValid(CrystalCatalyzerInventory.SLOT_CATALYST, stack)) {
            return inventory.insertItem(slot, stack, simulate);
        }

        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot != CrystalCatalyzerInventory.SLOT_OUTPUT) {
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

        if (slot == CrystalCatalyzerInventory.SLOT_MATRIX
                || slot == CrystalCatalyzerInventory.SLOT_OUTPUT) {
            return false;
        }

        return slot == CrystalCatalyzerInventory.SLOT_CATALYST
                && inventory.isItemValid(CrystalCatalyzerInventory.SLOT_CATALYST, stack);
    }
}

