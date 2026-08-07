package com.moakiee.ae2lt.lightning.strike;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.recipe.RecipeContainerInput;

/**
 * Placeholder input: matching of {@link LightningStrikeRecipe} is performed
 * directly against the world, not via this container.
 */
public final class LightningStrikeRecipeInput extends RecipeContainerInput {
    public static final LightningStrikeRecipeInput EMPTY = new LightningStrikeRecipeInput();

    private LightningStrikeRecipeInput() {
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public ItemStack getItem(int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return 0;
    }
}
