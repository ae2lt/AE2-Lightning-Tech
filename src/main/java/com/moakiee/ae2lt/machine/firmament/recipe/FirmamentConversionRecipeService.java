package com.moakiee.ae2lt.machine.firmament.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.machine.firmament.FirmamentConversionInventory;
import com.moakiee.ae2lt.registry.ModRecipeTypes;

public final class FirmamentConversionRecipeService {
    private static final Comparator<RecipeHolder<FirmamentConversionRecipe>> RECIPE_ORDER = Comparator
            .<RecipeHolder<FirmamentConversionRecipe>>comparingInt(holder -> holder.value().priority())
            .reversed()
            .thenComparing(Comparator.comparingInt(
                    (RecipeHolder<FirmamentConversionRecipe> holder) -> holder.value().inputs().size()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (RecipeHolder<FirmamentConversionRecipe> holder) -> holder.value().totalInputCount()).reversed())
            .thenComparing(holder -> holder.id().toString());

    private FirmamentConversionRecipeService() {
    }

    public static Optional<FirmamentConversionRecipeCandidate> findFirstProcessable(
            Level level,
            FirmamentConversionInventory inventory) {
        if (level == null) {
            return Optional.empty();
        }

        FirmamentConversionRecipeInput input = FirmamentConversionRecipeInput.fromInventory(inventory);
        if (input.isEmpty()) {
            return Optional.empty();
        }

        List<RecipeHolder<FirmamentConversionRecipe>> recipes =
                new ArrayList<>(level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.FIRMAMENT_CONVERSION_TYPE.get()));
        recipes.sort(RECIPE_ORDER);

        for (RecipeHolder<FirmamentConversionRecipe> recipe : recipes) {
            Optional<FirmamentConversionRecipeMatch> match = recipe.value().planMatch(input);
            if (match.isEmpty()) {
                continue;
            }
            if (!canAcceptOutputs(inventory, recipe.value().getResultStacks())) {
                continue;
            }
            return Optional.of(new FirmamentConversionRecipeCandidate(recipe, match.get()));
        }

        return Optional.empty();
    }

    public static Optional<RecipeHolder<FirmamentConversionRecipe>> findRecipeById(Level level, ResourceLocation recipeId) {
        if (level == null || recipeId == null) {
            return Optional.empty();
        }

        for (RecipeHolder<FirmamentConversionRecipe> recipe
                : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.FIRMAMENT_CONVERSION_TYPE.get())) {
            if (recipe.id().equals(recipeId)) {
                return Optional.of(recipe);
            }
        }

        return Optional.empty();
    }

    public static Optional<FirmamentConversionRecipeCandidate> findLockedRecipeMatch(
            Level level,
            FirmamentConversionInventory inventory,
            FirmamentConversionLockedRecipe lockedRecipe) {
        if (level == null || lockedRecipe == null) {
            return Optional.empty();
        }

        Optional<RecipeHolder<FirmamentConversionRecipe>> recipe = findRecipeById(level, lockedRecipe.recipeId());
        if (recipe.isEmpty() || recipe.get().value().processTime() != lockedRecipe.processTime()) {
            return Optional.empty();
        }

        FirmamentConversionRecipeInput input = FirmamentConversionRecipeInput.fromInventory(inventory);
        if (input.isEmpty()) {
            return Optional.empty();
        }

        Optional<FirmamentConversionRecipeMatch> match = recipe.get().value().planMatch(input);
        if (match.isEmpty()) {
            return Optional.empty();
        }
        if (!canAcceptOutputs(inventory, recipe.get().value().getResultStacks())) {
            return Optional.empty();
        }

        return Optional.of(new FirmamentConversionRecipeCandidate(recipe.get(), match.get()));
    }

    public static boolean canAcceptOutput(FirmamentConversionInventory inventory, ItemStack result) {
        return inventory.canAcceptRecipeOutput(result);
    }

    public static boolean canAcceptOutputs(FirmamentConversionInventory inventory, List<ItemStack> results) {
        return inventory.canAcceptRecipeOutputs(results);
    }
}
