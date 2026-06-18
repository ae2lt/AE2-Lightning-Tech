package com.moakiee.ae2lt.machine.firmament.recipe;

import java.util.Arrays;
import java.util.List;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.machine.firmament.FirmamentConversionInventory;

public final class FirmamentConversionRecipeMatch {
    private final int[] inputConsumptions;

    public FirmamentConversionRecipeMatch(int[] inputConsumptions) {
        if (inputConsumptions.length != 3) {
            throw new IllegalArgumentException("inputConsumptions must have length 3");
        }
        this.inputConsumptions = Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public int getConsumptionForSlot(int slot) {
        if (slot < FirmamentConversionInventory.SLOT_INPUT_0
                || slot > FirmamentConversionInventory.SLOT_INPUT_2) {
            throw new IllegalArgumentException("slot must be one of the three input slots");
        }
        return inputConsumptions[slot];
    }

    public int[] inputConsumptions() {
        return Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public boolean canFitResult(FirmamentConversionInventory inventory, ItemStack result) {
        return inventory.canAcceptRecipeOutput(result);
    }

    public boolean canFitResults(FirmamentConversionInventory inventory, List<ItemStack> results) {
        return inventory.canAcceptRecipeOutputs(results);
    }
}
