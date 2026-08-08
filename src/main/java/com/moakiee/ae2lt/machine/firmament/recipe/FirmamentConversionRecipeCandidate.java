package com.moakiee.ae2lt.machine.firmament.recipe;

import net.minecraft.resources.ResourceLocation;

/**
 * A matched firmament conversion recipe together with its registry id.
 * 1.20.1's {@code Recipe} carries no id (1.21 wraps recipes in {@code RecipeHolder}),
 * so the id is captured separately from {@code RecipeManager}.
 */
public record FirmamentConversionRecipeCandidate(
        ResourceLocation id,
        FirmamentConversionRecipe recipe,
        FirmamentConversionRecipeMatch match) {

    public ResourceLocation recipeId() {
        return id;
    }
}
