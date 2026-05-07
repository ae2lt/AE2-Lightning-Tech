package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModRecipeTypes;

public final class OverloadProcessingRecipeService {
    public static final int EXTREME_TO_HIGH_RATIO = 4;

    private static final Comparator<RecipeHolder<OverloadProcessingRecipe>> RECIPE_ORDER = Comparator
            .<RecipeHolder<OverloadProcessingRecipe>>comparingInt(holder -> holder.value().priority())
            .reversed()
            .thenComparing(Comparator.comparingInt(
                    (RecipeHolder<OverloadProcessingRecipe> holder) -> holder.value().itemInputs().size()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (RecipeHolder<OverloadProcessingRecipe> holder) -> holder.value().totalInputCount()).reversed())
            .thenComparing(holder -> holder.id().toString());

    private static Object cachedRawRecipeList;
    private static RecipeManager cachedRecipeManager;
    private static List<RecipeHolder<OverloadProcessingRecipe>> sortedRecipeCache;

    private OverloadProcessingRecipeService() {
    }

    private static List<RecipeHolder<OverloadProcessingRecipe>> getSortedRecipes(Level level) {
        RecipeManager recipeManager = level.getRecipeManager();
        var raw = recipeManager.getAllRecipesFor(ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get());
        if (recipeManager != cachedRecipeManager || raw != cachedRawRecipeList || sortedRecipeCache == null) {
            sortedRecipeCache = new ArrayList<>(raw);
            sortedRecipeCache.sort(RECIPE_ORDER);
            cachedRecipeManager = recipeManager;
            cachedRawRecipeList = raw;
        }
        return sortedRecipeCache;
    }

    public static Optional<OverloadProcessingRecipeCandidate> findFirstProcessable(
            Level level,
            OverloadProcessingFactoryInventory inventory,
            FluidStack inputFluid,
            FluidStack outputFluid,
            long availableHighVoltage,
            long availableExtremeHighVoltage) {
        if (level == null) {
            return Optional.empty();
        }

        OverloadProcessingRecipeInput input = OverloadProcessingRecipeInput.fromInventory(inventory, inputFluid);
        if (input.isEmpty()) {
            return Optional.empty();
        }

        List<RecipeHolder<OverloadProcessingRecipe>> recipes = getSortedRecipes(level);

        for (RecipeHolder<OverloadProcessingRecipe> recipe : recipes) {
            int parallel = findMaxParallel(
                    recipe.value(),
                    input,
                    inventory,
                    outputFluid,
                    inventory.getInstalledParallelCapacity(),
                    availableHighVoltage,
                    availableExtremeHighVoltage);
            if (parallel <= 0) {
                continue;
            }

            Optional<OverloadProcessingRecipeMatch> match = recipe.value().planMatch(input, parallel);
            if (match.isEmpty()) {
                continue;
            }

            return Optional.of(new OverloadProcessingRecipeCandidate(
                    recipe,
                    match.get(),
                    parallel,
                    computeTotalEnergy(recipe.value().totalEnergy(), parallel),
                    (long) recipe.value().lightningCost() * parallel));
        }

        return Optional.empty();
    }

    public static Optional<RecipeHolder<OverloadProcessingRecipe>> findRecipeById(Level level, ResourceLocation recipeId) {
        if (level == null || recipeId == null) {
            return Optional.empty();
        }

        for (RecipeHolder<OverloadProcessingRecipe> recipe
                : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get())) {
            if (recipe.id().equals(recipeId)) {
                return Optional.of(recipe);
            }
        }

        return Optional.empty();
    }

    public static Optional<OverloadProcessingRecipeCandidate> findLockedRecipeMatch(
            Level level,
            OverloadProcessingFactoryInventory inventory,
            FluidStack inputFluid,
            FluidStack outputFluid,
            OverloadProcessingLockedRecipe lockedRecipe,
            long availableHighVoltage,
            long availableExtremeHighVoltage) {
        if (level == null || lockedRecipe == null || lockedRecipe.parallel() <= 0) {
            return Optional.empty();
        }

        Optional<RecipeHolder<OverloadProcessingRecipe>> recipe = findRecipeById(level, lockedRecipe.recipeId());
        if (recipe.isEmpty()) {
            return Optional.empty();
        }

        OverloadProcessingRecipeInput input = OverloadProcessingRecipeInput.fromInventory(inventory, inputFluid);
        if (input.isEmpty()) {
            return Optional.empty();
        }

        Optional<OverloadProcessingRecipeMatch> match = recipe.get().value().planMatch(input, lockedRecipe.parallel());
        if (match.isEmpty()) {
            return Optional.empty();
        }
        if (computeTotalEnergy(recipe.get().value().totalEnergy(), lockedRecipe.parallel()) != lockedRecipe.totalEnergy()) {
            return Optional.empty();
        }
        if (resolveLightningConsumption(
                inventory,
                lockedRecipe.lightningTier(),
                lockedRecipe.totalLightningCost(),
                availableHighVoltage,
                availableExtremeHighVoltage).isEmpty()) {
            return Optional.empty();
        }
        if (!canAcceptOutputs(inventory, recipe.get().value(), outputFluid, lockedRecipe.parallel())) {
            return Optional.empty();
        }

        return Optional.of(new OverloadProcessingRecipeCandidate(
                recipe.get(),
                match.get(),
                lockedRecipe.parallel(),
                lockedRecipe.totalEnergy(),
                lockedRecipe.totalLightningCost()));
    }

    public static long computeTotalEnergy(long singleOperationEnergy, int parallel) {
        if (singleOperationEnergy <= 0L || parallel <= 0) {
            return 0L;
        }

        try {
            int maxParallel = OverloadProcessingFactoryInventory.getMaxParallel();
            if (maxParallel <= 1) {
                return Math.multiplyExact(singleOperationEnergy, parallel);
            }
            long divisor = (long) (maxParallel * 2 - 2);
            long numeratorFactor = (long) (parallel + maxParallel * 2 - 3);
            long linearEnergy = Math.multiplyExact(singleOperationEnergy, parallel);
            long scaled = Math.multiplyExact(linearEnergy, numeratorFactor);
            return divideCeil(scaled, divisor);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    public static Optional<LightningConsumptionPlan> resolveLightningConsumption(
            OverloadProcessingFactoryInventory inventory,
            LightningKey.Tier lightningTier,
            long lightningCost,
            long availableHighVoltage,
            long availableExtremeHighVoltage) {
        if (lightningCost <= 0L) {
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

        if (!inventory.hasLightningCollapseMatrix()) {
            return Optional.empty();
        }

        long extremeUsed = availableExtremeHighVoltage;
        long remaining = lightningCost - extremeUsed;
        long highVoltageNeeded = remaining * EXTREME_TO_HIGH_RATIO;
        if (highVoltageNeeded < 0L || availableHighVoltage < highVoltageNeeded) {
            return Optional.empty();
        }
        if (extremeUsed > 0L) {
            return Optional.of(new LightningConsumptionPlan(
                    LightningKey.EXTREME_HIGH_VOLTAGE, extremeUsed,
                    LightningKey.HIGH_VOLTAGE, highVoltageNeeded,
                    true));
        }
        return Optional.of(new LightningConsumptionPlan(
                LightningKey.HIGH_VOLTAGE, highVoltageNeeded, true));
    }

    public static long getEquivalentHighVoltageCost(LightningKey.Tier lightningTier, long lightningCost) {
        return lightningTier == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                ? lightningCost * EXTREME_TO_HIGH_RATIO
                : lightningCost;
    }

    private static int findMaxParallel(
            OverloadProcessingRecipe recipe,
            OverloadProcessingRecipeInput input,
            OverloadProcessingFactoryInventory inventory,
            FluidStack outputFluid,
            int parallelCapacity,
            long availableHighVoltage,
            long availableExtremeHighVoltage) {
        int upper = parallelCapacity;
        if (upper <= 0) {
            return 0;
        }

        FluidStack requiredInputFluid = recipe.fluidInput();
        if (!requiredInputFluid.isEmpty()) {
            if (input.inputFluid().isEmpty()
                    || !FluidStack.isSameFluidSameComponents(requiredInputFluid, input.inputFluid())) {
                return 0;
            }
            upper = Math.min(upper, input.inputFluid().getAmount() / requiredInputFluid.getAmount());
        }

        upper = Math.min(upper, maxLightningParallel(recipe, inventory, availableHighVoltage, availableExtremeHighVoltage));
        if (upper <= 0) {
            return 0;
        }

        int low = 1;
        int high = upper;
        int best = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (recipe.planMatch(input, mid).isPresent()
                    && canAcceptOutputs(inventory, recipe, outputFluid, mid)
                    && recipe.hasRequiredFluid(input.inputFluid(), mid)) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private static int maxLightningParallel(
            OverloadProcessingRecipe recipe,
            OverloadProcessingFactoryInventory inventory,
            long availableHighVoltage,
            long availableExtremeHighVoltage) {
        if (recipe.lightningCost() <= 0) {
            return 0;
        }

        if (recipe.lightningTier() == LightningKey.Tier.HIGH_VOLTAGE) {
            return (int) Math.min(Integer.MAX_VALUE, availableHighVoltage / recipe.lightningCost());
        }

        long exactParallel = availableExtremeHighVoltage / recipe.lightningCost();
        if (!inventory.hasLightningCollapseMatrix()) {
            return (int) Math.min(Integer.MAX_VALUE, exactParallel);
        }

        long substitutedParallel = availableHighVoltage / ((long) recipe.lightningCost() * EXTREME_TO_HIGH_RATIO);
        long totalParallel = exactParallel + substitutedParallel;
        if (totalParallel < 0L) totalParallel = Long.MAX_VALUE;
        return (int) Math.min(Integer.MAX_VALUE, totalParallel);
    }

    private static boolean canAcceptOutputs(
            OverloadProcessingFactoryInventory inventory,
            OverloadProcessingRecipe recipe,
            FluidStack outputFluid,
            int parallel) {
        if (!inventory.canAcceptRecipeOutputs(recipe.getScaledItemResults(parallel))) {
            return false;
        }

        FluidStack scaledFluid = recipe.getScaledFluidResult(parallel);
        if (scaledFluid.isEmpty()) {
            return true;
        }

        if (outputFluid.isEmpty()) {
            return scaledFluid.getAmount()
                    <= com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity.OUTPUT_TANK_CAPACITY;
        }

        return FluidStack.isSameFluidSameComponents(outputFluid, scaledFluid)
                && outputFluid.getAmount() + scaledFluid.getAmount()
                <= com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity.OUTPUT_TANK_CAPACITY;
    }

    private static long divideCeil(long dividend, long divisor) {
        if (divisor <= 0L) {
            throw new IllegalArgumentException("divisor must be positive");
        }
        if (dividend <= 0L) {
            return 0L;
        }
        return (dividend + divisor - 1L) / divisor;
    }

    public record LightningConsumptionPlan(
            LightningKey primaryKey, long primaryAmount,
            LightningKey secondaryKey, long secondaryAmount,
            boolean matrixSubstitution) {

        public LightningConsumptionPlan(LightningKey key, long amount, boolean matrixSubstitution) {
            this(key, amount, null, 0L, matrixSubstitution);
        }

        public boolean hasSecondary() {
            return secondaryKey != null && secondaryAmount > 0L;
        }
    }
}

