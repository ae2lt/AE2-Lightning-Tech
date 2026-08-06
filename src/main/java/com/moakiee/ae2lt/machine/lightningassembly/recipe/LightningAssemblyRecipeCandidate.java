package com.moakiee.ae2lt.machine.lightningassembly.recipe;

import net.minecraft.world.item.crafting.RecipeHolder;

public record LightningAssemblyRecipeCandidate(
        RecipeHolder<LightningAssemblyRecipe> recipe,
        LightningAssemblyRecipeMatch match) {
}
