package com.moakiee.ae2lt.machine.lightningchamber.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.util.RecipeManagerByTypeAccess;

public final class LightningSimulationRecipeService {
    public static final int EXTREME_TO_HIGH_RATIO = 4;

    private static final Comparator<LightningSimulationRecipe> RECIPE_ORDER = Comparator
            .comparingInt(LightningSimulationRecipe::priority)
            .reversed()
            .thenComparing(Comparator.comparingInt((LightningSimulationRecipe recipe) -> recipe.inputs().size()).reversed())
            .thenComparing(Comparator.comparingInt(LightningSimulationRecipe::totalInputCount).reversed())
            .thenComparing(recipe -> recipe.getId().toString());

    private static RecipeManager cachedRecipeManager;
    private static List<LightningSimulationRecipe> sortedRecipeCache;
    private static int cachedRecipeOrderFingerprint;

    private LightningSimulationRecipeService() {
    }

    private static synchronized List<LightningSimulationRecipe> getSortedRecipes(Level level) {
        RecipeManager recipeManager = level.getRecipeManager();
        List<LightningSimulationRecipe> raw = recipeManager
                .getAllRecipesFor(ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get());
        int orderFingerprint = computeRecipeOrderFingerprint(raw);
        if (recipeManager != cachedRecipeManager
                || orderFingerprint != cachedRecipeOrderFingerprint
                || sortedRecipeCache == null) {
            sortedRecipeCache = new ArrayList<>(raw);
            sortedRecipeCache.sort(RECIPE_ORDER);
            cachedRecipeManager = recipeManager;
            cachedRecipeOrderFingerprint = orderFingerprint;
        }
        return sortedRecipeCache;
    }

    private static int computeRecipeOrderFingerprint(List<LightningSimulationRecipe> recipes) {
        int hash = 1;
        for (LightningSimulationRecipe recipe : recipes) {
            hash = 31 * hash + recipe.getId().hashCode();
            hash = 31 * hash + System.identityHashCode(recipe);
            hash = 31 * hash + recipe.priority();
            hash = 31 * hash + recipe.inputs().size();
            hash = 31 * hash + recipe.totalInputCount();
        }
        return hash;
    }

    public static Optional<LightningSimulationRecipeCandidate> findFirstProcessable(
            Level level,
            LightningSimulationChamberInventory inventory,
            long availableHighVoltage,
            long availableExtremeHighVoltage) {
        if (level == null) {
            return Optional.empty();
        }

        LightningSimulationRecipeInput input = LightningSimulationRecipeInput.fromInventory(inventory);
        if (input.isEmpty()) {
            return Optional.empty();
        }

        List<LightningSimulationRecipe> recipes = getSortedRecipes(level);

        for (LightningSimulationRecipe recipe : recipes) {
            Optional<LightningSimulationRecipeMatch> match = recipe.planMatch(input);
            if (match.isEmpty()) {
                continue;
            }
            if (resolveLightningConsumption(
                    inventory,
                    recipe.lightningTier(),
                    recipe.lightningCost(),
                    availableHighVoltage,
                    availableExtremeHighVoltage).isEmpty()) {
                continue;
            }

            if (!canAcceptOutput(inventory, recipe.getResultStack())) {
                continue;
            }

            return Optional.of(new LightningSimulationRecipeCandidate(recipe, match.get()));
        }

        return Optional.empty();
    }

    public static Optional<LightningSimulationRecipe> findRecipeById(Level level, ResourceLocation recipeId) {
        if (level == null || recipeId == null) {
            return Optional.empty();
        }

        return RecipeManagerByTypeAccess.findById(
                level.getRecipeManager(),
                ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get(),
                recipeId);
    }

    public static Optional<LightningSimulationRecipeCandidate> findLockedRecipeMatch(
            Level level,
            LightningSimulationChamberInventory inventory,
            LightningSimulationLockedRecipe lockedRecipe,
            long availableHighVoltage,
            long availableExtremeHighVoltage) {
        if (level == null || lockedRecipe == null) {
            return Optional.empty();
        }

        Optional<LightningSimulationRecipe> recipe = findRecipeById(level, lockedRecipe.recipeId());
        if (recipe.isEmpty()) {
            return Optional.empty();
        }

        LightningSimulationRecipeInput input = LightningSimulationRecipeInput.fromInventory(inventory);
        if (input.isEmpty()) {
            return Optional.empty();
        }

        Optional<LightningSimulationRecipeMatch> match = recipe.get().planMatch(input);
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

        return Optional.of(new LightningSimulationRecipeCandidate(recipe.get(), match.get()));
    }

    public static Optional<LightningSimulationRecipeCandidate> findLockedRecipeMatchIgnoringLightning(
            Level level,
            LightningSimulationChamberInventory inventory,
            LightningSimulationLockedRecipe lockedRecipe) {
        if (level == null || lockedRecipe == null) {
            return Optional.empty();
        }

        Optional<LightningSimulationRecipe> recipe = findRecipeById(level, lockedRecipe.recipeId());
        if (recipe.isEmpty()) {
            return Optional.empty();
        }

        LightningSimulationRecipeInput input = LightningSimulationRecipeInput.fromInventory(inventory);
        if (input.isEmpty()) {
            return Optional.empty();
        }

        Optional<LightningSimulationRecipeMatch> match = recipe.get().planMatch(input);
        if (match.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new LightningSimulationRecipeCandidate(recipe.get(), match.get()));
    }

    public static Optional<LightningConsumptionPlan> resolveLightningConsumption(
            LightningSimulationChamberInventory inventory,
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

    public static boolean canAcceptOutput(LightningSimulationChamberInventory inventory, ItemStack result) {
        return inventory.canAcceptRecipeOutput(result);
    }

    public record LightningConsumptionPlan(LightningKey key, long amount, boolean matrixSubstitution) {
    }
}
