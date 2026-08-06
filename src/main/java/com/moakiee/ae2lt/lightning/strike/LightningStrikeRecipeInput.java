package com.moakiee.ae2lt.lightning.strike;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * Placeholder input: matching of {@link LightningStrikeRecipe} is performed
 * directly against the world, not via a {@link RecipeInput}.
 */
public final class LightningStrikeRecipeInput implements RecipeInput {
    public static final LightningStrikeRecipeInput EMPTY = new LightningStrikeRecipeInput();

    private LightningStrikeRecipeInput() {
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
