package com.moakiee.ae2lt.machine.lightningchamber;

import com.moakiee.ae2lt.machine.common.AutomationItemResourceHandler;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Capability-facing inventory wrapper.
 *
 * <p>External automation only inserts into recipe input slots. The dedicated
 * catalyst matrix slot is reserved for manual GUI placement.</p>
 */
public class LightningSimulationChamberAutomationInventory extends AutomationItemResourceHandler {
    public LightningSimulationChamberAutomationInventory(LightningSimulationChamberInventory inventory) {
        super(inventory);
    }

    @Override
    protected boolean canInsert(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        return !stack.isEmpty()
                && slot != LightningSimulationChamberInventory.SLOT_OUTPUT
                && slot != LightningSimulationChamberInventory.SLOT_CATALYST
                && ((LightningSimulationChamberInventory) inventory).isInputSlot(slot);
    }

    @Override
    protected boolean canExtract(int slot, ItemResource resource) {
        validateSlotIndex(slot);
        return slot == LightningSimulationChamberInventory.SLOT_OUTPUT;
    }
}
