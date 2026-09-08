package com.moakiee.ae2lt.mixin.recipeviewer.emi;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Retains the exact JEI recipe, including native recipe identity and mold requirements. */
@Pseudo
@Mixin(targets = "dev.emi.emi.jemi.JemiRecipe", remap = false)
public interface JemiRecipeAccessor {
    @Accessor("recipe")
    Object ae2lt$getRecipe();
}
