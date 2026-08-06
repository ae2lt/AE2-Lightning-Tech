package com.moakiee.ae2lt.machine.firmament;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;

/**
 * Seven-slot inventory for the Firmament Conversion Core.
 *
 * <p>Slot layout:
 * 0-2 = unordered recipe inputs
 * 3-6 = output only
 */
public class FirmamentConversionInventory extends LargeStackItemHandler {
    public static final int SLOT_INPUT_0 = 0;
    public static final int SLOT_INPUT_1 = 1;
    public static final int SLOT_INPUT_2 = 2;
    public static final int SLOT_OUTPUT_0 = 3;
    public static final int SLOT_OUTPUT_1 = 4;
    public static final int SLOT_OUTPUT_2 = 5;
    public static final int SLOT_OUTPUT_3 = 6;
    public static final int SLOT_OUTPUT = SLOT_OUTPUT_0;

    public static final int INPUT_SLOT_COUNT = 3;
    public static final int OUTPUT_SLOT_COUNT = 4;
    public static final int SLOT_COUNT = 7;
    public static final int SLOT_LIMIT = 64;

    public FirmamentConversionInventory(@Nullable Runnable changeListener) {
        super(SLOT_COUNT, changeListener);
    }

    @Override
    public int getSlotLimit(int slot) {
        validateSlotIndex(slot);
        return SLOT_LIMIT;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        if (stack.isEmpty()) {
            return false;
        }
        return isInputSlot(slot);
    }

    public boolean isInputSlot(int slot) {
        return slot >= SLOT_INPUT_0 && slot <= SLOT_INPUT_2;
    }

    public boolean isOutputSlot(int slot) {
        return slot >= SLOT_OUTPUT_0 && slot <= SLOT_OUTPUT_3;
    }

    public ItemStack insertRecipeOutput(ItemStack stack, boolean simulate) {
        return insertIntoOutputs(stack, simulate);
    }

    public boolean canAcceptRecipeOutput(ItemStack stack) {
        return insertRecipeOutput(stack, true).isEmpty();
    }

    public boolean canAcceptRecipeOutputs(List<ItemStack> outputs) {
        if (outputs.isEmpty()) {
            return false;
        }

        ItemStack[] simulated = new ItemStack[OUTPUT_SLOT_COUNT];
        for (int index = 0; index < OUTPUT_SLOT_COUNT; index++) {
            simulated[index] = getStackInSlot(SLOT_OUTPUT_0 + index).copy();
        }

        for (ItemStack stack : outputs) {
            if (stack.isEmpty() || !insertIntoOutputArray(simulated, stack).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public boolean insertRecipeOutputs(List<ItemStack> outputs) {
        if (!canAcceptRecipeOutputs(outputs)) {
            return false;
        }

        for (ItemStack stack : outputs) {
            if (!insertIntoOutputs(stack, false).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private ItemStack insertIntoOutputs(ItemStack stack, boolean simulate) {
        ItemStack remainder = stack;

        for (int slot = SLOT_OUTPUT_0; slot <= SLOT_OUTPUT_3; slot++) {
            if (!getStackInSlot(slot).isEmpty()) {
                remainder = insertItemUnchecked(slot, remainder, simulate);
                if (remainder.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }

        for (int slot = SLOT_OUTPUT_0; slot <= SLOT_OUTPUT_3; slot++) {
            if (getStackInSlot(slot).isEmpty()) {
                remainder = insertItemUnchecked(slot, remainder, simulate);
                if (remainder.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }

        return remainder;
    }

    private ItemStack insertIntoOutputArray(ItemStack[] simulated, ItemStack stack) {
        ItemStack remainder = stack.copy();

        for (int index = 0; index < simulated.length; index++) {
            ItemStack existing = simulated[index];
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remainder)) {
                continue;
            }

            int freeSpace = SLOT_LIMIT - existing.getCount();
            if (freeSpace <= 0) {
                continue;
            }

            int toInsert = Math.min(freeSpace, remainder.getCount());
            simulated[index] = existing.copyWithCount(existing.getCount() + toInsert);
            if (toInsert == remainder.getCount()) {
                return ItemStack.EMPTY;
            }
            remainder.shrink(toInsert);
        }

        for (int index = 0; index < simulated.length; index++) {
            if (!simulated[index].isEmpty()) {
                continue;
            }

            int toInsert = Math.min(SLOT_LIMIT, remainder.getCount());
            simulated[index] = remainder.copyWithCount(toInsert);
            if (toInsert == remainder.getCount()) {
                return ItemStack.EMPTY;
            }
            remainder.shrink(toInsert);
        }

        return remainder;
    }
}
