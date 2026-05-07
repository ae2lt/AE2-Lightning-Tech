package com.moakiee.ae2lt.menu;

import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngSlot;

/**
 * Menu slot that honors the backing inventory's slot limit even when it
 * exceeds the item's vanilla max stack size.
 */
public class LargeStackAppEngSlot extends AppEngSlot {
    public LargeStackAppEngSlot(InternalInventory inventory, int slot) {
        super(inventory, slot);
        setHideAmount(true);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return getMaxStackSize();
    }
}
