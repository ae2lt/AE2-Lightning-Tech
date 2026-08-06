package com.moakiee.ae2lt.machine.overloadfactory.recipe;

public record OverloadProcessingRecipeCandidate(
        OverloadProcessingRecipe recipe,
        OverloadProcessingRecipeMatch match,
        int parallel,
        long totalEnergy,
        long totalLightningCost) {
}
