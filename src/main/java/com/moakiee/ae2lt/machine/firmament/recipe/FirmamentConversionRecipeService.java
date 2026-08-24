package com.moakiee.ae2lt.machine.firmament.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.machine.firmament.FirmamentConversionInventory;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.util.RecipeManagerByTypeAccess;

public final class FirmamentConversionRecipeService {
    private static final Comparator<Map.Entry<ResourceLocation, FirmamentConversionRecipe>> RECIPE_ORDER = Comparator
            .<Map.Entry<ResourceLocation, FirmamentConversionRecipe>>comparingInt(entry -> entry.getValue().priority())
            .reversed()
            .thenComparing(Comparator.comparingInt(
                    (Map.Entry<ResourceLocation, FirmamentConversionRecipe> entry) -> entry.getValue().inputs().size()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (Map.Entry<ResourceLocation, FirmamentConversionRecipe> entry) -> entry.getValue().totalInputCount()).reversed())
            .thenComparing(entry -> entry.getKey().toString());

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

        List<Map.Entry<ResourceLocation, FirmamentConversionRecipe>> recipes =
                new ArrayList<>(RecipeManagerByTypeAccess.byType(
                        level.getRecipeManager(), ModRecipeTypes.FIRMAMENT_CONVERSION_TYPE.get()).entrySet());
        recipes.sort(RECIPE_ORDER);

        for (Map.Entry<ResourceLocation, FirmamentConversionRecipe> entry : recipes) {
            FirmamentConversionRecipe recipe = entry.getValue();
            Optional<FirmamentConversionRecipeMatch> match = recipe.planMatch(input);
            if (match.isEmpty()) {
                continue;
            }
            if (!canAcceptOutputs(inventory, recipe.getResultStacks())) {
                continue;
            }
            return Optional.of(new FirmamentConversionRecipeCandidate(entry.getKey(), recipe, match.get()));
        }

        return Optional.empty();
    }

    public static Optional<FirmamentConversionRecipeCandidate> findRecipeById(Level level, ResourceLocation recipeId) {
        if (level == null || recipeId == null) {
            return Optional.empty();
        }

        return RecipeManagerByTypeAccess.findById(
                        level.getRecipeManager(),
                        ModRecipeTypes.FIRMAMENT_CONVERSION_TYPE.get(),
                        recipeId)
                .map(recipe -> new FirmamentConversionRecipeCandidate(recipeId, recipe, null));
    }

    public static Optional<FirmamentConversionRecipeCandidate> findLockedRecipeMatch(
            Level level,
            FirmamentConversionInventory inventory,
            FirmamentConversionLockedRecipe lockedRecipe) {
        if (level == null || lockedRecipe == null) {
            return Optional.empty();
        }

        Optional<FirmamentConversionRecipeCandidate> recipe = findRecipeById(level, lockedRecipe.recipeId());
        if (recipe.isEmpty() || recipe.get().recipe().processTime() != lockedRecipe.processTime()) {
            return Optional.empty();
        }

        FirmamentConversionRecipeInput input = FirmamentConversionRecipeInput.fromInventory(inventory);
        if (input.isEmpty()) {
            return Optional.empty();
        }

        Optional<FirmamentConversionRecipeMatch> match = recipe.get().recipe().planMatch(input);
        if (match.isEmpty()) {
            return Optional.empty();
        }
        if (!canAcceptOutputs(inventory, recipe.get().recipe().getResultStacks())) {
            return Optional.empty();
        }

        return Optional.of(new FirmamentConversionRecipeCandidate(
                recipe.get().recipeId(), recipe.get().recipe(), match.get()));
    }

    public static boolean canAcceptOutput(FirmamentConversionInventory inventory, ItemStack result) {
        return inventory.canAcceptRecipeOutput(result);
    }

    public static boolean canAcceptOutputs(FirmamentConversionInventory inventory, List<ItemStack> results) {
        return inventory.canAcceptRecipeOutputs(results);
    }
}
