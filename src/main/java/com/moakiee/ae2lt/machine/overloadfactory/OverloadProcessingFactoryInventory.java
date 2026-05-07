package com.moakiee.ae2lt.machine.overloadfactory;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;
import com.moakiee.ae2lt.registry.ModItems;

public class OverloadProcessingFactoryInventory extends LargeStackItemHandler {
    public static final int SLOT_INPUT_0 = 0;
    public static final int SLOT_INPUT_8 = 8;
    public static final int SLOT_MATRIX = 9;
    public static final int SLOT_OUTPUT_0 = 10;

    public static final int SLOT_COUNT = 11;
    public static final int INPUT_SLOT_COUNT = 9;
    public static final int OUTPUT_SLOT_COUNT = 1;
    public static final int LARGE_SLOT_LIMIT = 8192;
    public static final int MATRIX_SLOT_LIMIT = 64;
    public OverloadProcessingFactoryInventory(@Nullable Runnable changeListener) {
        super(SLOT_COUNT, changeListener);
    }

    @Override
    public int getSlotLimit(int slot) {
        validateSlotIndex(slot);
        return slot == SLOT_MATRIX ? MATRIX_SLOT_LIMIT : LARGE_SLOT_LIMIT;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        if (stack.isEmpty()) {
            return false;
        }
        if (isOutputSlot(slot)) {
            return false;
        }

        if (slot == SLOT_MATRIX) {
            return isLightningCollapseMatrix(stack);
        }

        return isInputSlot(slot);
    }

    public boolean isInputSlot(int slot) {
        return slot >= SLOT_INPUT_0 && slot <= SLOT_INPUT_8;
    }

    public boolean isOutputSlot(int slot) {
        return slot == SLOT_OUTPUT_0;
    }

    public boolean isLightningCollapseMatrix(ItemStack stack) {
        return stack.is(ModItems.LIGHTNING_COLLAPSE_MATRIX.get());
    }

    public int getInstalledMatrixCount() {
        ItemStack stack = getStackInSlot(SLOT_MATRIX);
        return isLightningCollapseMatrix(stack) ? Math.min(MATRIX_SLOT_LIMIT, stack.getCount()) : 0;
    }

    public boolean hasLightningCollapseMatrix() {
        return getInstalledMatrixCount() > 0;
    }

    public int getInstalledParallelCapacity() {
        int matrixCount = getInstalledMatrixCount();
        if (matrixCount <= 0) {
            // Without any lightning collapse matrix installed, the factory still operates at a
            // minimum of one parallel so basic processing is always available. Matrix-specific
            // bonuses (extra parallelism, EHV↔HV substitution) are retained in other code paths.
            return 1;
        }
        return getMaxParallelForMatrixCount(matrixCount);
    }

    public static int getMaxParallelForMatrixCount(int matrixCount) {
        if (matrixCount <= 0) {
            return 0;
        }
        int parallelPerMatrix = AE2LTCommonConfig.overloadFactoryParallelPerMatrix();
        int maxParallel = getMaxParallel();
        long scaledParallel = (long) matrixCount * parallelPerMatrix;
        return (int) Math.min(maxParallel, Math.min(Integer.MAX_VALUE, scaledParallel));
    }

    public static int getMaxParallel() {
        long maxParallel = (long) MATRIX_SLOT_LIMIT * AE2LTCommonConfig.overloadFactoryParallelPerMatrix();
        return (int) Math.min(Integer.MAX_VALUE, maxParallel);
    }

    public boolean canAcceptRecipeOutputs(List<ItemStack> outputs) {
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
        for (ItemStack stack : outputs) {
            if (stack.isEmpty() || !insertIntoOutputs(stack, false).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void setClientRenderStack(int slot, ItemStack stack) {
        setStackInSlotUnchecked(slot, stack);
    }

    private ItemStack insertIntoOutputs(ItemStack stack, boolean simulate) {
        ItemStack remainder = stack;

        for (int slot = SLOT_OUTPUT_0; slot < SLOT_OUTPUT_0 + OUTPUT_SLOT_COUNT; slot++) {
            if (!getStackInSlot(slot).isEmpty()) {
                remainder = insertItemUnchecked(slot, remainder, simulate);
                if (remainder.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }

        for (int slot = SLOT_OUTPUT_0; slot < SLOT_OUTPUT_0 + OUTPUT_SLOT_COUNT; slot++) {
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

            int freeSpace = LARGE_SLOT_LIMIT - existing.getCount();
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

            int toInsert = Math.min(LARGE_SLOT_LIMIT, remainder.getCount());
            simulated[index] = remainder.copyWithCount(toInsert);
            if (toInsert == remainder.getCount()) {
                return ItemStack.EMPTY;
            }
            remainder.shrink(toInsert);
        }

        return remainder;
    }
}
