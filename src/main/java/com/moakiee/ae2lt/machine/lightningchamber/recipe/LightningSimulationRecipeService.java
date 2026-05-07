package com.moakiee.ae2lt.machine.lightningchamber.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModRecipeTypes;

public final class LightningSimulationRecipeService {
    public static final int EXTREME_TO_HIGH_RATIO = 4;

    private static final Comparator<RecipeHolder<LightningSimulationRecipe>> RECIPE_ORDER = Comparator
            .<RecipeHolder<LightningSimulationRecipe>>comparingInt(holder -> holder.value().priority())
            .reversed()
            .thenComparing(Comparator.comparingInt(
                    (RecipeHolder<LightningSimulationRecipe> holder) -> holder.value().inputs().size()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (RecipeHolder<LightningSimulationRecipe> holder) -> holder.value().totalInputCount()).reversed())
            .thenComparing(holder -> holder.id().toString());

    private LightningSimulationRecipeService() {
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

        List<RecipeHolder<LightningSimulationRecipe>> recipes =
                new ArrayList<>(level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get()));
        recipes.sort(RECIPE_ORDER);

        for (RecipeHolder<LightningSimulationRecipe> recipe : recipes) {
            Optional<LightningSimulationRecipeMatch> match = recipe.value().planMatch(input);
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

            return Optional.of(new LightningSimulationRecipeCandidate(recipe, match.get()));
        }

        return Optional.empty();
    }

    public static Optional<RecipeHolder<LightningSimulationRecipe>> findRecipeById(Level level, ResourceLocation recipeId) {
        if (level == null || recipeId == null) {
            return Optional.empty();
        }

        for (RecipeHolder<LightningSimulationRecipe> recipe
                : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get())) {
            if (recipe.id().equals(recipeId)) {
                return Optional.of(recipe);
            }
        }

        return Optional.empty();
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

        Optional<RecipeHolder<LightningSimulationRecipe>> recipe = findRecipeById(level, lockedRecipe.recipeId());
        if (recipe.isEmpty()) {
            return Optional.empty();
        }

        LightningSimulationRecipeInput input = LightningSimulationRecipeInput.fromInventory(inventory);
        if (input.isEmpty()) {
            return Optional.empty();
        }

        Optional<LightningSimulationRecipeMatch> match = recipe.get().value().planMatch(input);
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

        Optional<RecipeHolder<LightningSimulationRecipe>> recipe = findRecipeById(level, lockedRecipe.recipeId());
        if (recipe.isEmpty()) {
            return Optional.empty();
        }

        LightningSimulationRecipeInput input = LightningSimulationRecipeInput.fromInventory(inventory);
        if (input.isEmpty()) {
            return Optional.empty();
        }

        Optional<LightningSimulationRecipeMatch> match = recipe.get().value().planMatch(input);
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
