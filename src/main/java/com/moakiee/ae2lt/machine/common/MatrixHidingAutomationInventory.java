package com.moakiee.ae2lt.machine.common;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Common automation view for machines with a dedicated Lightning Collapse
 * Matrix slot.
 *
 * <p>The physical slot layout remains unchanged so output slot indices stay
 * stable, but automation sees the matrix slot as empty and cannot write to it.
 * This keeps permanent machine upgrades out of recipe-inventory scans.</p>
 */
public abstract class MatrixHidingAutomationInventory<T extends IItemHandlerModifiable>
        implements IItemHandlerModifiable {
    protected final T inventory;

    private final int matrixSlot;

    protected MatrixHidingAutomationInventory(T inventory, int matrixSlot) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.matrixSlot = matrixSlot;
    }

    @Override
    public final int getSlots() {
        return inventory.getSlots();
    }

    @Override
    public final ItemStack getStackInSlot(int slot) {
        return isSlotVisibleToAutomation(slot, matrixSlot)
                ? inventory.getStackInSlot(slot)
                : ItemStack.EMPTY;
    }

    @Override
    public final void setStackInSlot(int slot, ItemStack stack) {
        if (isSlotVisibleToAutomation(slot, matrixSlot)) {
            inventory.setStackInSlot(slot, stack);
        }
    }

    @Override
    public final int getSlotLimit(int slot) {
        return inventory.getSlotLimit(slot);
    }

    static boolean isSlotVisibleToAutomation(int slot, int matrixSlot) {
        return slot != matrixSlot;
    }
}
