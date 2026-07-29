package com.moakiee.ae2lt.machine.crystalcatalyzer;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.machine.common.MatrixHidingAutomationInventory;

/**
 * Capability-facing inventory wrapper.
 *
 * <p>Automation may insert catalysts into slot 0. The dedicated matrix slot is
 * reserved for manual GUI placement, and extraction is restricted to output.</p>
 */
public class CrystalCatalyzerAutomationInventory
        extends MatrixHidingAutomationInventory<CrystalCatalyzerInventory> {
    public CrystalCatalyzerAutomationInventory(CrystalCatalyzerInventory inventory) {
        super(inventory, CrystalCatalyzerInventory.SLOT_MATRIX);
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
