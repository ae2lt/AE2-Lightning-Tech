package com.moakiee.ae2lt.util;

import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Small bridge for 1.20.1's protected recipe-type map lookup.
 */
public interface RecipeManagerByTypeAccess {
    <C extends Container, T extends Recipe<C>> Map<ResourceLocation, T> ae2lt$getByType(RecipeType<T> type);

    static <C extends Container, T extends Recipe<C>> Map<ResourceLocation, T> byType(
            RecipeManager recipeManager,
            RecipeType<T> type) {
        return ((RecipeManagerByTypeAccess) recipeManager).ae2lt$getByType(type);
    }

    static <C extends Container, T extends Recipe<C>> Optional<T> findById(
            RecipeManager recipeManager,
            RecipeType<T> type,
            ResourceLocation recipeId) {
        return Optional.ofNullable(byType(recipeManager, type).get(recipeId));
    }
}
