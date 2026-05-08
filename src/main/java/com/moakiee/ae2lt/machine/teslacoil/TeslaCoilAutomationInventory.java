package com.moakiee.ae2lt.machine.teslacoil;

import com.moakiee.ae2lt.machine.common.AutomationItemResourceHandler;

import net.minecraft.world.item.ItemStack;

public class TeslaCoilAutomationInventory extends AutomationItemResourceHandler {
    public TeslaCoilAutomationInventory(TeslaCoilInventory inventory) {
        super(inventory);
    }

    @Override
    protected boolean canInsert(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        return !stack.isEmpty()
                && slot != TeslaCoilInventory.SLOT_MATRIX
                && inventory.isItemValid(slot, stack);
    }
}
