package com.moakiee.ae2lt.machine.lightningchamber.recipe;

import java.util.Arrays;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;

public final class LightningSimulationRecipeMatch {
    private final int[] inputConsumptions;

    public LightningSimulationRecipeMatch(int[] inputConsumptions) {
        if (inputConsumptions.length != 3) {
            throw new IllegalArgumentException("inputConsumptions must have length 3");
        }
        this.inputConsumptions = Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public int getConsumptionForSlot(int slot) {
        if (slot < LightningSimulationChamberInventory.SLOT_INPUT_0
                || slot > LightningSimulationChamberInventory.SLOT_INPUT_2) {
            throw new IllegalArgumentException("slot must be one of the three input slots");
        }
        return inputConsumptions[slot];
    }

    public int[] inputConsumptions() {
        return Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public boolean canFitResult(LightningSimulationChamberInventory inventory, ItemStack result) {
        return inventory.canAcceptRecipeOutput(result);
    }
}
