package com.moakiee.ae2lt.machine.teslacoil;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;
import com.moakiee.ae2lt.registry.ModItems;

public class TeslaCoilInventory extends LargeStackItemHandler {
    public static final int SLOT_DUST = 0;
    public static final int SLOT_MATRIX = 1;
    public static final int SLOT_COUNT = 2;
    public static final int DUST_SLOT_LIMIT = 1024;

    public TeslaCoilInventory(@Nullable Runnable changeListener) {
        super(SLOT_COUNT, changeListener);
    }

    @Override
    public int getSlotLimit(int slot) {
        validateSlotIndex(slot);
        return slot == SLOT_MATRIX ? 1 : DUST_SLOT_LIMIT;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        if (stack.isEmpty()) {
            return false;
        }

        return switch (slot) {
            case SLOT_DUST -> isOverloadCrystalDust(stack);
            case SLOT_MATRIX -> isLightningCollapseMatrix(stack);
            default -> false;
        };
    }

    public boolean isOverloadCrystalDust(ItemStack stack) {
        return stack.is(ModItems.OVERLOAD_CRYSTAL_DUST.get());
    }

    public boolean isLightningCollapseMatrix(ItemStack stack) {
        return stack.is(ModItems.LIGHTNING_COLLAPSE_MATRIX.get());
    }

    public boolean hasRequiredDust(int amount) {
        return getStackInSlot(SLOT_DUST).getCount() >= amount;
    }

    public boolean hasMatrix() {
        return !getStackInSlot(SLOT_MATRIX).isEmpty();
    }
}
