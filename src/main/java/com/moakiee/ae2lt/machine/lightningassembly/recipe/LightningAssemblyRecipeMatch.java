package com.moakiee.ae2lt.machine.lightningassembly.recipe;

import java.util.Arrays;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.machine.lightningassembly.LightningAssemblyChamberInventory;

public final class LightningAssemblyRecipeMatch {
    private final int[] inputConsumptions;

    public LightningAssemblyRecipeMatch(int[] inputConsumptions) {
        if (inputConsumptions.length != 9) {
            throw new IllegalArgumentException("inputConsumptions must have length 9");
        }
        this.inputConsumptions = Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public int getConsumptionForSlot(int slot) {
        if (slot < LightningAssemblyChamberInventory.SLOT_INPUT_0
                || slot > LightningAssemblyChamberInventory.SLOT_INPUT_8) {
            throw new IllegalArgumentException("slot must be one of the nine input slots");
        }
        return inputConsumptions[slot];
    }

    public int[] inputConsumptions() {
        return Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public boolean canFitResult(LightningAssemblyChamberInventory inventory, ItemStack result) {
        return inventory.canAcceptRecipeOutput(result);
    }
}
