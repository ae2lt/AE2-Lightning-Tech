package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;
import com.moakiee.ae2lt.registry.ModRecipeTypes;

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
        for (RecipeHolder<CrystalCatalyzerRecipe> holder : getRecipes(level)) {
            CrystalCatalyzerRecipe recipe = holder.value();
            if (recipe.mode() != mode) {
                continue;
            }
            if (recipe.getOutputTemplate().isEmpty()) {
                continue;
            }
            if (recipe.matches(input, level)) {
                return Optional.of(new CrystalCatalyzerRecipeCandidate(holder));
            }
        }
        return Optional.empty();
    }

    public static Optional<CrystalCatalyzerRecipeCandidate> findRecipeById(
            @Nullable Level level,
            net.minecraft.resources.ResourceLocation recipeId) {
        if (level == null) {
            return Optional.empty();
        }
        return level.getRecipeManager()
                .byKey(recipeId)
                .flatMap(CrystalCatalyzerRecipeService::toCrystalCatalyzerCandidate);
    }

    public static boolean isKnownCatalyst(@Nullable Level level, ItemStack stack) {
        return isKnownCatalyst(level, stack, Mode.CRYSTAL);
    }

    public static boolean isKnownCatalyst(@Nullable Level level, ItemStack stack, Mode mode) {
        if (level == null || stack.isEmpty()) {
            return false;
        }
        for (RecipeHolder<CrystalCatalyzerRecipe> holder : getRecipes(level)) {
            CrystalCatalyzerRecipe recipe = holder.value();
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

    private static List<RecipeHolder<CrystalCatalyzerRecipe>> getRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get());
    }

    private static Optional<CrystalCatalyzerRecipeCandidate> toCrystalCatalyzerCandidate(RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        if (!(recipe instanceof CrystalCatalyzerRecipe crystalCatalyzerRecipe)
                || recipe.getType() != ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get()) {
            return Optional.empty();
        }

        return Optional.of(new CrystalCatalyzerRecipeCandidate(
                new RecipeHolder<>(holder.id(), crystalCatalyzerRecipe)));
    }
}
