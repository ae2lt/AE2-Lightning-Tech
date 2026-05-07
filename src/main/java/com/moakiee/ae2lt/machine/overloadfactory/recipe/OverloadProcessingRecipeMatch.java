package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.Arrays;

import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;

public final class OverloadProcessingRecipeMatch {
    private final int[] inputConsumptions;

    public OverloadProcessingRecipeMatch(int[] inputConsumptions) {
        if (inputConsumptions.length != OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT) {
            throw new IllegalArgumentException("inputConsumptions must have length 9");
        }
        this.inputConsumptions = Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public int getConsumptionForSlot(int slot) {
        if (slot < OverloadProcessingFactoryInventory.SLOT_INPUT_0
                || slot > OverloadProcessingFactoryInventory.SLOT_INPUT_8) {
            throw new IllegalArgumentException("slot must be an input slot");
        }
        return inputConsumptions[slot];
    }

    public int[] inputConsumptions() {
        return Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }
}
