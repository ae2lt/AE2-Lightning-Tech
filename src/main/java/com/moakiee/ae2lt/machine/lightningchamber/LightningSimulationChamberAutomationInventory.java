package com.moakiee.ae2lt.machine.lightningchamber;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.machine.common.MatrixHidingAutomationInventory;

/**
 * Capability-facing inventory wrapper.
 *
 * <p>External automation only inserts into recipe input slots. The dedicated
 * catalyst matrix slot is reserved for manual GUI placement.</p>
 */
public class LightningSimulationChamberAutomationInventory
        extends MatrixHidingAutomationInventory<LightningSimulationChamberInventory> {
    public LightningSimulationChamberAutomationInventory(LightningSimulationChamberInventory inventory) {
        super(inventory, LightningSimulationChamberInventory.SLOT_CATALYST);
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
