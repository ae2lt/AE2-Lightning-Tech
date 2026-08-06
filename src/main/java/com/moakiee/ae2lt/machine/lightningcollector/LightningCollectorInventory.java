package com.moakiee.ae2lt.machine.lightningcollector;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;
import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.world.item.ItemStack;

public class LightningCollectorInventory extends LargeStackItemHandler {
    public static final int SLOT_CRYSTAL = 0;
    public static final int SLOT_COUNT = 1;

    public LightningCollectorInventory(@Nullable Runnable changeListener) {
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
        return slot == SLOT_CRYSTAL
                && (stack.is(ModItems.ELECTRO_CHIME_CRYSTAL.get())
                || stack.is(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL.get()));
    }

    public void setClientRenderStack(ItemStack stack) {
        setStackInSlotUnchecked(SLOT_CRYSTAL, stack);
    }
}
