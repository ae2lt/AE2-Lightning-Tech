package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import net.minecraft.world.item.crafting.RecipeHolder;

public record OverloadProcessingRecipeCandidate(
        RecipeHolder<OverloadProcessingRecipe> recipe,
        OverloadProcessingRecipeMatch match,
        int parallel,
        long totalEnergy,
        long totalLightningCost) {
}
