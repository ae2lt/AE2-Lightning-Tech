package com.moakiee.ae2lt.machine.common;

import appeng.api.inventories.InternalInventory;

import net.minecraft.world.item.ItemStack;

/**
 * Capability-facing item wrapper for machines whose exposed slots are inputs.
 */
public class InsertOnlyAutomationInventory extends AutomationItemResourceHandler {
    public InsertOnlyAutomationInventory(InternalInventory inventory) {
        super(inventory);
    }
}
