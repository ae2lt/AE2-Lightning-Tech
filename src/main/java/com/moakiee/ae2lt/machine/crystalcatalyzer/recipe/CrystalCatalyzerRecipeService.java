package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.util.RecipeManagerByTypeAccess;

public final class CrystalCatalyzerRecipeService {
    private CrystalCatalyzerRecipeService() {
    }

    public static Optional<CrystalCatalyzerRecipeCandidate> findRecipe(
            @Nullable Level level,
            CrystalCatalyzerInventory inventory) {
        return findRecipe(level, inventory, Mode.CRYSTAL);
    }

    public static Optional<CrystalCatalyzerRecipeCandidate> findRecipe(
            @Nullable Level level,
            CrystalCatalyzerInventory inventory,
            Mode mode) {
        if (level == null) {
            return Optional.empty();
        }

        CrystalCatalyzerRecipeInput input = CrystalCatalyzerRecipeInput.fromMachine(inventory);
        for (CrystalCatalyzerRecipe recipe : getRecipes(level)) {
            if (recipe.mode() != mode) {
                continue;
            }
            if (recipe.getOutputTemplate().isEmpty()) {
                continue;
            }
            if (recipe.matches(input, level)) {
                return Optional.of(new CrystalCatalyzerRecipeCandidate(recipe));
            }
        }
        return Optional.empty();
    }

    public static Optional<CrystalCatalyzerRecipeCandidate> findRecipeById(
            @Nullable Level level,
            ResourceLocation recipeId) {
        if (level == null) {
            return Optional.empty();
        }
        var recipe = RecipeManagerByTypeAccess.findById(
                level.getRecipeManager(),
                ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get(),
                recipeId).orElse(null);
        return recipe == null ? Optional.empty() : Optional.of(new CrystalCatalyzerRecipeCandidate(recipe));
    }

    public static boolean isKnownCatalyst(@Nullable Level level, ItemStack stack) {
        return isKnownCatalyst(level, stack, Mode.CRYSTAL);
    }

    public static boolean isKnownCatalyst(@Nullable Level level, ItemStack stack, Mode mode) {
        if (level == null || stack.isEmpty()) {
            return false;
        }
        for (CrystalCatalyzerRecipe recipe : getRecipes(level)) {
            if (recipe.mode() != mode) {
                continue;
            }
            if (recipe.getOutputTemplate().isEmpty()) {
                continue;
            }
            var catalyst = recipe.catalyst();
            if (catalyst.isPresent() && catalyst.get().test(stack)) {
                return true;
            }
        }
        return false;
    }

    private static List<CrystalCatalyzerRecipe> getRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get());
    }
}
