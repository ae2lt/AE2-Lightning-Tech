package com.moakiee.ae2lt.machine.lightningassembly.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.machine.lightningassembly.LightningAssemblyChamberInventory;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModRecipeTypes;

public final class LightningAssemblyRecipeService {
    public static final int EXTREME_TO_HIGH_RATIO = 4;

    private static final Comparator<RecipeHolder<LightningAssemblyRecipe>> RECIPE_ORDER = Comparator
            .<RecipeHolder<LightningAssemblyRecipe>>comparingInt(holder -> holder.value().priority())
            .reversed()
            .thenComparing(Comparator.comparingInt(
                    (RecipeHolder<LightningAssemblyRecipe> holder) -> holder.value().inputs().size()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (RecipeHolder<LightningAssemblyRecipe> holder) -> holder.value().totalInputCount()).reversed())
            .thenComparing(holder -> holder.id().toString());

    private static List<RecipeHolder<LightningAssemblyRecipe>> cachedRawRecipeList;
    private static RecipeManager cachedRecipeManager;
    private static List<RecipeHolder<LightningAssemblyRecipe>> sortedRecipeCache;
    private static int cachedRecipeOrderFingerprint;

    private LightningAssemblyRecipeService() {
    }

    private static synchronized List<RecipeHolder<LightningAssemblyRecipe>> getSortedRecipes(Level level) {
        RecipeManager recipeManager = level.getRecipeManager();
        var raw = recipeManager.getAllRecipesFor(ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE.get());
        int orderFingerprint = computeRecipeOrderFingerprint(raw);
        if (recipeManager != cachedRecipeManager
                || raw != cachedRawRecipeList
                || orderFingerprint != cachedRecipeOrderFingerprint
                || sortedRecipeCache == null) {
            sortedRecipeCache = new ArrayList<>(raw);
            sortedRecipeCache.sort(RECIPE_ORDER);
            cachedRecipeManager = recipeManager;
            cachedRawRecipeList = raw;
            cachedRecipeOrderFingerprint = orderFingerprint;
        }
        return sortedRecipeCache;
    }

    private static int computeRecipeOrderFingerprint(List<RecipeHolder<LightningAssemblyRecipe>> recipes) {
        int hash = 1;
        for (var holder : recipes) {
            var recipe = holder.value();
            hash = 31 * hash + holder.id().hashCode();
            hash = 31 * hash + recipe.priority();
            hash = 31 * hash + recipe.inputs().size();
            hash = 31 * hash + recipe.totalInputCount();
        }
        return hash;
    }

    public static synchronized void invalidateSortedRecipeCache() {
        cachedRawRecipeList = null;
        cachedRecipeManager = null;
        sortedRecipeCache = null;
        cachedRecipeOrderFingerprint = 0;
    }

    public static Optional<LightningAssemblyRecipeCandidate> findFirstProcessable(
            Level level,
            LightningAssemblyChamberInventory inventory,
            long availableHighVoltage,
            long availableExtremeHighVoltage) {
        if (level == null) {
            return Optional.empty();
        }

        LightningAssemblyRecipeInput input = LightningAssemblyRecipeInput.fromInventory(inventory);
        if (input.isEmpty()) {
            return Optional.empty();
        }

        List<RecipeHolder<LightningAssemblyRecipe>> recipes = getSortedRecipes(level);

        for (RecipeHolder<LightningAssemblyRecipe> recipe : recipes) {
            Optional<LightningAssemblyRecipeMatch> match = recipe.value().planMatch(input);
            if (match.isEmpty()) {
                continue;
            }
            if (resolveLightningConsumption(
                    inventory,
                    recipe.value().lightningTier(),
                    recipe.value().lightningCost(),
                    availableHighVoltage,
                    availableExtremeHighVoltage).isEmpty()) {
                continue;
            }

            if (!canAcceptOutput(inventory, recipe.value().getResultStack())) {
                continue;
            }

            return Optional.of(new LightningAssemblyRecipeCandidate(recipe, match.get()));
        }

        return Optional.empty();
    }

    public static Optional<RecipeHolder<LightningAssemblyRecipe>> findRecipeById(Level level, ResourceLocation recipeId) {
        if (level == null || recipeId == null) {
            return Optional.empty();
        }

        for (RecipeHolder<LightningAssemblyRecipe> recipe
                : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE.get())) {
            if (recipe.id().equals(recipeId)) {
                return Optional.of(recipe);
            }
        }

        return Optional.empty();
    }

    public static Optional<LightningAssemblyRecipeCandidate> findLockedRecipeMatch(
            Level level,
            LightningAssemblyChamberInventory inventory,
            LightningAssemblyLockedRecipe lockedRecipe,
            long availableHighVoltage,
            long availableExtremeHighVoltage) {
        if (level == null || lockedRecipe == null) {
            return Optional.empty();
        }

        Optional<RecipeHolder<LightningAssemblyRecipe>> recipe = findRecipeById(level, lockedRecipe.recipeId());
        if (recipe.isEmpty()) {
            return Optional.empty();
        }

        LightningAssemblyRecipeInput input = LightningAssemblyRecipeInput.fromInventory(inventory);
        if (input.isEmpty()) {
            return Optional.empty();
        }

        Optional<LightningAssemblyRecipeMatch> match = recipe.get().value().planMatch(input);
        if (match.isEmpty()) {
            return Optional.empty();
        }
        if (resolveLightningConsumption(
                inventory,
                lockedRecipe.lightningTier(),
                lockedRecipe.lightningCost(),
                availableHighVoltage,
                availableExtremeHighVoltage).isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new LightningAssemblyRecipeCandidate(recipe.get(), match.get()));
    }

    public static Optional<LightningAssemblyRecipeCandidate> findLockedRecipeMatchIgnoringLightning(
            Level level,
            LightningAssemblyChamberInventory inventory,
            LightningAssemblyLockedRecipe lockedRecipe) {
        if (level == null || lockedRecipe == null) {
            return Optional.empty();
        }

        Optional<RecipeHolder<LightningAssemblyRecipe>> recipe = findRecipeById(level, lockedRecipe.recipeId());
        if (recipe.isEmpty()) {
            return Optional.empty();
        }

        LightningAssemblyRecipeInput input = LightningAssemblyRecipeInput.fromInventory(inventory);
        if (input.isEmpty()) {
            return Optional.empty();
        }

        Optional<LightningAssemblyRecipeMatch> match = recipe.get().value().planMatch(input);
        if (match.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new LightningAssemblyRecipeCandidate(recipe.get(), match.get()));
    }

    public static Optional<LightningConsumptionPlan> resolveLightningConsumption(
            LightningAssemblyChamberInventory inventory,
            LightningKey.Tier lightningTier,
            int lightningCost,
            long availableHighVoltage,
            long availableExtremeHighVoltage) {
        if (lightningCost <= 0) {
            return Optional.empty();
        }

        if (lightningTier == LightningKey.Tier.HIGH_VOLTAGE) {
            return availableHighVoltage >= lightningCost
                    ? Optional.of(new LightningConsumptionPlan(LightningKey.HIGH_VOLTAGE, lightningCost, false))
                    : Optional.empty();
        }

        if (availableExtremeHighVoltage >= lightningCost) {
            return Optional.of(new LightningConsumptionPlan(
                    LightningKey.EXTREME_HIGH_VOLTAGE,
                    lightningCost,
                    false));
        }

        long highVoltageEquivalent = (long) lightningCost * EXTREME_TO_HIGH_RATIO;
        return inventory.hasLightningCollapseMatrix()
                && availableHighVoltage >= highVoltageEquivalent
                ? Optional.of(new LightningConsumptionPlan(LightningKey.HIGH_VOLTAGE, highVoltageEquivalent, true))
                : Optional.empty();
    }

    public static long getEquivalentHighVoltageCost(LightningKey.Tier lightningTier, int lightningCost) {
        return lightningTier == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                ? (long) lightningCost * EXTREME_TO_HIGH_RATIO
                : lightningCost;
    }

    public static boolean canAcceptOutput(LightningAssemblyChamberInventory inventory, ItemStack result) {
        return inventory.canAcceptRecipeOutput(result);
    }

    public record LightningConsumptionPlan(LightningKey key, long amount, boolean matrixSubstitution) {
    }
}
