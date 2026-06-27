package com.moakiee.ae2lt.machine.firmament.recipe;

import net.minecraft.world.item.crafting.RecipeHolder;

public record FirmamentConversionRecipeCandidate(
        RecipeHolder<FirmamentConversionRecipe> recipe,
        FirmamentConversionRecipeMatch match) {
}
