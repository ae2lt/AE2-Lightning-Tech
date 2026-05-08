package com.moakiee.ae2lt.machine.lightningassembly;

import com.moakiee.ae2lt.machine.common.AutomationItemResourceHandler;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Capability-facing inventory wrapper.
 *
 * <p>External automation only inserts into recipe input slots. The dedicated
 * catalyst matrix slot is reserved for manual GUI placement.</p>
 */
public class LightningAssemblyChamberAutomationInventory extends AutomationItemResourceHandler {
    public LightningAssemblyChamberAutomationInventory(LightningAssemblyChamberInventory inventory) {
        super(inventory);
    }

    @Override
    protected boolean canInsert(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        return !stack.isEmpty()
                && slot != LightningAssemblyChamberInventory.SLOT_OUTPUT
                && slot != LightningAssemblyChamberInventory.SLOT_CATALYST
                && ((LightningAssemblyChamberInventory) inventory).isInputSlot(slot);
    }

    @Override
    protected boolean canExtract(int slot, ItemResource resource) {
        validateSlotIndex(slot);
        return slot == LightningAssemblyChamberInventory.SLOT_OUTPUT;
    }
}
