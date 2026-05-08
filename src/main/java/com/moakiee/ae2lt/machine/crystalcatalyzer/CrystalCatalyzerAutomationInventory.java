package com.moakiee.ae2lt.machine.crystalcatalyzer;

import com.moakiee.ae2lt.machine.common.AutomationItemResourceHandler;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Capability-facing inventory wrapper.
 *
 * <p>Automation may insert catalysts into slot 0. The dedicated matrix slot is
 * reserved for manual GUI placement, and extraction is restricted to output.</p>
 */
public class CrystalCatalyzerAutomationInventory extends AutomationItemResourceHandler {
    public CrystalCatalyzerAutomationInventory(CrystalCatalyzerInventory inventory) {
        super(inventory);
    }

    @Override
    protected boolean canInsert(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        return !stack.isEmpty()
                && slot == CrystalCatalyzerInventory.SLOT_CATALYST
                && inventory.isItemValid(CrystalCatalyzerInventory.SLOT_CATALYST, stack);
    }

    @Override
    protected boolean canExtract(int slot, ItemResource resource) {
        validateSlotIndex(slot);
        return slot == CrystalCatalyzerInventory.SLOT_OUTPUT;
    }
}
