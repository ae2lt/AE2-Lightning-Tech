package com.moakiee.ae2lt.machine.atmosphericionizer;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.item.WeatherCondensateItem;
import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;

import net.minecraft.world.item.ItemStack;

public class AtmosphericIonizerInventory extends LargeStackItemHandler {
    public static final int SLOT_CONDENSATE = 0;
    public static final int SLOT_COUNT = 1;

    public AtmosphericIonizerInventory(@Nullable Runnable changeListener) {
        super(SLOT_COUNT, changeListener);
    }

    @Override
    public int getSlotLimit(int slot) {
        validateSlotIndex(slot);
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        return !stack.isEmpty() && stack.getItem() instanceof WeatherCondensateItem;
    }
}
