package com.moakiee.ae2lt.machine.lightningchamber;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Capability-facing inventory wrapper.
 *
 * <p>External automation only inserts into recipe input slots. The dedicated
 * catalyst matrix slot is reserved for manual GUI placement.</p>
 */
public class LightningSimulationChamberAutomationInventory implements IItemHandlerModifiable {
    private final LightningSimulationChamberInventory inventory;

    public LightningSimulationChamberAutomationInventory(LightningSimulationChamberInventory inventory) {
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
        inventory.validateSlotIndex(slot);
        Objects.requireNonNull(stack, "stack");

        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (slot == LightningSimulationChamberInventory.SLOT_OUTPUT
                || slot == LightningSimulationChamberInventory.SLOT_CATALYST) {
            return stack;
        }

        if (inventory.isInputSlot(slot)) {
            return inventory.insertItem(slot, stack, simulate);
        }

        return stack;
    }

    /**
     * Convenience path for machine-adjacent automation that wants "best effort"
     * insertion instead of targeting a particular physical slot.
     */
    public ItemStack insertItem(ItemStack stack, boolean simulate) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = stack;
        for (int slot = LightningSimulationChamberInventory.SLOT_INPUT_0;
             slot <= LightningSimulationChamberInventory.SLOT_INPUT_2;
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
        if (slot != LightningSimulationChamberInventory.SLOT_OUTPUT) {
            inventory.validateSlotIndex(slot);
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
        inventory.validateSlotIndex(slot);
        if (stack.isEmpty()) {
            return false;
        }
        if (slot == LightningSimulationChamberInventory.SLOT_OUTPUT
                || slot == LightningSimulationChamberInventory.SLOT_CATALYST) {
            return false;
        }

        return inventory.isInputSlot(slot);
    }
}

