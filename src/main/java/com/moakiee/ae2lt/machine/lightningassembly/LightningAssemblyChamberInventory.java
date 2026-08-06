package com.moakiee.ae2lt.machine.lightningassembly;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;
import com.moakiee.ae2lt.registry.ModItems;

/**
 * Real machine inventory for the lightning assembly chamber.
 *
 * <p>Slot layout:
 * 0-8  = unordered recipe inputs
 * 9    = optional lightning collapse matrix
 * 10   = output only
 */
public class LightningAssemblyChamberInventory extends LargeStackItemHandler {
    public static final int SLOT_INPUT_0 = 0;
    public static final int SLOT_INPUT_1 = 1;
    public static final int SLOT_INPUT_2 = 2;
    public static final int SLOT_INPUT_3 = 3;
    public static final int SLOT_INPUT_4 = 4;
    public static final int SLOT_INPUT_5 = 5;
    public static final int SLOT_INPUT_6 = 6;
    public static final int SLOT_INPUT_7 = 7;
    public static final int SLOT_INPUT_8 = 8;
    public static final int SLOT_CATALYST = 9;
    public static final int SLOT_OUTPUT = 10;

    public static final int SLOT_COUNT = 11;
    public static final int LARGE_SLOT_LIMIT = 8192;

    public LightningAssemblyChamberInventory(@Nullable Runnable changeListener) {
        super(SLOT_COUNT, changeListener);
    }

    @Override
    public int getSlotLimit(int slot) {
        validateSlotIndex(slot);
        return LARGE_SLOT_LIMIT;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        if (stack.isEmpty()) {
            return false;
        }

        if (slot == SLOT_OUTPUT) {
            return false;
        }

        if (slot == SLOT_CATALYST) {
            return isCatalystItem(stack);
        }

        return isInputSlot(slot);
    }

    public boolean isInputSlot(int slot) {
        return slot >= SLOT_INPUT_0 && slot <= SLOT_INPUT_8;
    }

    public boolean isLightningCollapseMatrix(ItemStack stack) {
        return stack.is(ModItems.LIGHTNING_COLLAPSE_MATRIX.get());
    }

    public boolean isCatalystItem(ItemStack stack) {
        return isLightningCollapseMatrix(stack);
    }

    public boolean hasLightningCollapseMatrix() {
        return isLightningCollapseMatrix(getStackInSlot(SLOT_CATALYST));
    }

    /**
     * Internal machine-only output insertion.
     *
     * <p>Slot 10 rejects normal external insertion by design, so recipe completion
     * must use this method instead of the public insertItem path.</p>
     */
    public ItemStack insertRecipeOutput(ItemStack stack, boolean simulate) {
        return insertItemUnchecked(SLOT_OUTPUT, stack, simulate);
    }

    public boolean canAcceptRecipeOutput(ItemStack stack) {
        return insertRecipeOutput(stack, true).isEmpty();
    }

    public void setClientRenderStack(int slot, ItemStack stack) {
        setStackInSlotUnchecked(slot, stack);
    }
}
