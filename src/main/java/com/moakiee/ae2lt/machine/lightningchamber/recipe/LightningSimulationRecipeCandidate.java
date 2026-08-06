package com.moakiee.ae2lt.machine.lightningchamber.recipe;

import net.minecraft.world.item.crafting.RecipeHolder;

public record LightningSimulationRecipeCandidate(
        RecipeHolder<LightningSimulationRecipe> recipe,
        LightningSimulationRecipeMatch match) {
}
