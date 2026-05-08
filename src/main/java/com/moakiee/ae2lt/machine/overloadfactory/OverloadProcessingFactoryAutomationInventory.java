package com.moakiee.ae2lt.machine.overloadfactory;

import com.moakiee.ae2lt.machine.common.AutomationItemResourceHandler;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

public class OverloadProcessingFactoryAutomationInventory extends AutomationItemResourceHandler {
    public OverloadProcessingFactoryAutomationInventory(OverloadProcessingFactoryInventory inventory) {
        super(inventory);
    }

    @Override
    protected boolean canInsert(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        var machineInventory = (OverloadProcessingFactoryInventory) inventory;
        return !stack.isEmpty()
                && slot != OverloadProcessingFactoryInventory.SLOT_MATRIX
                && !machineInventory.isOutputSlot(slot)
                && machineInventory.isInputSlot(slot);
    }

    @Override
    protected boolean canExtract(int slot, ItemResource resource) {
        validateSlotIndex(slot);
        return ((OverloadProcessingFactoryInventory) inventory).isOutputSlot(slot);
    }
}
