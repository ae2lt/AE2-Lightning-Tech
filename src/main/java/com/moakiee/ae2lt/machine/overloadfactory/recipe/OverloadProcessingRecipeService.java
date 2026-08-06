package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

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

    private static final Comparator<SelectionKey> SELECTION_KEY_ORDER = Comparator
            .comparingInt(SelectionKey::parallel).reversed()
            .thenComparing(Comparator.comparingInt(SelectionKey::priority).reversed())
            .thenComparing(Comparator.comparingInt(SelectionKey::itemInputKinds).reversed())
            .thenComparing(Comparator.comparingInt(SelectionKey::totalInputCount).reversed())
            .thenComparing(SelectionKey::recipeId);

    private static RecipeManager cachedRecipeManager;
    private static List<RecipeHolder<OverloadProcessingRecipe>> sortedRecipeCache;
    private static int cachedRecipeOrderFingerprint;

    private OverloadProcessingRecipeService() {
    }

    private static synchronized List<RecipeHolder<OverloadProcessingRecipe>> getSortedRecipes(Level level) {
        RecipeManager recipeManager = level.getRecipeManager();
        var raw = recipeManager.getAllRecipesFor(ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get());
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

    private static int computeRecipeOrderFingerprint(List<RecipeHolder<OverloadProcessingRecipe>> recipes) {
        int hash = 1;
        for (var holder : recipes) {
            var recipe = holder.value();
            hash = 31 * hash + holder.id().hashCode();
            hash = 31 * hash + System.identityHashCode(recipe);
            hash = 31 * hash + recipe.priority();
            hash = 31 * hash + recipe.itemInputs().size();
            hash = 31 * hash + recipe.totalInputCount();
        }
        return hash;
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
        int parallelCapacity = inventory.getInstalledParallelCapacity();

        OverloadProcessingRecipeCandidate bestCandidate = null;
        SelectionKey bestKey = null;
        for (RecipeHolder<OverloadProcessingRecipe> recipe : recipes) {
            Optional<OverloadProcessingRecipeCandidate> candidate = evaluateCandidate(
                    recipe,
                    input,
                    inventory,
                    outputFluid,
                    parallelCapacity,
                    availableHighVoltage,
                    availableExtremeHighVoltage);
            if (candidate.isEmpty()) {
                continue;
            }

            SelectionKey candidateKey = selectionKey(recipe, candidate.get().parallel());
            if (bestKey == null || SELECTION_KEY_ORDER.compare(candidateKey, bestKey) < 0) {
                bestCandidate = candidate.get();
                bestKey = candidateKey;
            }
        }
        return Optional.ofNullable(bestCandidate);
    }

    private static Optional<OverloadProcessingRecipeCandidate> evaluateCandidate(
            RecipeHolder<OverloadProcessingRecipe> recipe,
            OverloadProcessingRecipeInput input,
            OverloadProcessingFactoryInventory inventory,
            FluidStack outputFluid,
            int parallelCapacity,
            long availableHighVoltage,
            long availableExtremeHighVoltage) {
            Optional<ParallelMatch> parallelMatch = findMaxParallel(
                    recipe.value(),
                    input,
                    inventory,
                    outputFluid,
                    parallelCapacity,
                    availableHighVoltage,
                    availableExtremeHighVoltage);
            if (parallelMatch.isEmpty()) {
                return Optional.empty();
            }
            var match = parallelMatch.get();

            return Optional.of(new OverloadProcessingRecipeCandidate(
                    recipe,
                    match.match(),
                    match.parallel(),
                    computeTotalEnergy(recipe.value().totalEnergy(), match.parallel()),
                    (long) recipe.value().lightningCost() * match.parallel()));
    }

    public static Optional<RecipeHolder<OverloadProcessingRecipe>> findRecipeById(Level level, ResourceLocation recipeId) {
        if (level == null || recipeId == null) {
            return Optional.empty();
        }

        return level.getRecipeManager()
                .byKey(recipeId)
                .flatMap(holder -> {
                    var recipe = holder.value();
                    if (!(recipe instanceof OverloadProcessingRecipe overloadRecipe)
                            || recipe.getType() != ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get()) {
                        return Optional.empty();
                    }
                    return Optional.of(new RecipeHolder<>(holder.id(), overloadRecipe));
                });
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
        Optional<OverloadProcessingRecipeMatch> match = recipe.get().value().planMatch(input, lockedRecipe.parallel());
        if (match.isEmpty()) {
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
        return resolveLightningConsumption(
                inventory.hasLightningCollapseMatrix(),
                lightningTier,
                lightningCost,
                availableHighVoltage,
                availableExtremeHighVoltage);
    }

    static Optional<LightningConsumptionPlan> resolveLightningConsumption(
            boolean hasLightningCollapseMatrix,
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

        if (!hasLightningCollapseMatrix) {
            return Optional.empty();
        }

        long extremeUsed = availableExtremeHighVoltage;
        long remaining = lightningCost - extremeUsed;
        if (remaining > Long.MAX_VALUE / EXTREME_TO_HIGH_RATIO) {
            return Optional.empty();
        }
        long highVoltageNeeded = remaining * EXTREME_TO_HIGH_RATIO;
        if (availableHighVoltage < highVoltageNeeded) {
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

    private static Optional<ParallelMatch> findMaxParallel(
            OverloadProcessingRecipe recipe,
            OverloadProcessingRecipeInput input,
            OverloadProcessingFactoryInventory inventory,
            FluidStack outputFluid,
            int parallelCapacity,
            long availableHighVoltage,
            long availableExtremeHighVoltage) {
        int upper = parallelCapacity;
        if (upper <= 0) {
            return Optional.empty();
        }

        FluidStack requiredInputFluid = recipe.fluidInput();
        if (!requiredInputFluid.isEmpty()) {
            if (input.inputFluid().isEmpty()
                    || !FluidStack.isSameFluidSameComponents(requiredInputFluid, input.inputFluid())) {
                return Optional.empty();
            }
            upper = Math.min(upper, input.inputFluid().getAmount() / requiredInputFluid.getAmount());
        }

        upper = Math.min(upper, maxLightningParallel(recipe, inventory, availableHighVoltage, availableExtremeHighVoltage));
        if (upper <= 0) {
            return Optional.empty();
        }

        Optional<OverloadProcessingRecipe.MatchPlan> plan = recipe.prepareMatch(input);
        if (plan.isEmpty()) {
            return Optional.empty();
        }

        upper = (int) Math.min(upper, plan.get().maxOperationsByAvailability());
        upper = (int) Math.min(upper, maxOutputParallel(inventory, recipe, outputFluid));
        if (upper <= 0) {
            return Optional.empty();
        }

        // The capacity bound above is exact for at most one item result (the
        // current recipe limit); with several distinct results competing for
        // empty slots, fall back to simulating output placement per probe.
        boolean checkOutputsPerProbe = recipe.rawItemResults().size() > 1;

        int low = 1;
        int high = upper;
        int best = 0;
        OverloadProcessingRecipeMatch bestMatch = null;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (checkOutputsPerProbe && !canAcceptOutputs(inventory, recipe, outputFluid, mid)) {
                high = mid - 1;
                continue;
            }

            Optional<OverloadProcessingRecipeMatch> match = plan.get().allocate(mid);
            if (match.isPresent()) {
                best = mid;
                bestMatch = match.get();
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return bestMatch == null ? Optional.empty() : Optional.of(new ParallelMatch(best, bestMatch));
    }

    /**
     * Upper bound on parallel operations from output space: item output slot
     * capacity plus output tank headroom. Mirrors canAcceptOutputs without
     * allocating scaled result stacks per probe.
     */
    private static long maxOutputParallel(
            OverloadProcessingFactoryInventory inventory,
            OverloadProcessingRecipe recipe,
            FluidStack outputFluid) {
        long bound = Long.MAX_VALUE;

        List<ItemStack> itemResults = recipe.rawItemResults();
        if (itemResults.size() == 1) {
            ItemStack result = itemResults.getFirst();
            bound = inventory.getOutputCapacityFor(result) / result.getCount();
        }

        FluidStack fluidResult = recipe.rawFluidResult();
        if (!fluidResult.isEmpty()) {
            long tankCapacity = com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity.OUTPUT_TANK_CAPACITY;
            long space;
            if (outputFluid.isEmpty()) {
                space = tankCapacity;
            } else if (FluidStack.isSameFluidSameComponents(outputFluid, fluidResult)) {
                space = tankCapacity - outputFluid.getAmount();
            } else {
                space = 0L;
            }
            bound = Math.min(bound, Math.max(0L, space) / fluidResult.getAmount());
        }

        return bound;
    }

    private record ParallelMatch(int parallel, OverloadProcessingRecipeMatch match) {
    }

    private static int maxLightningParallel(
            OverloadProcessingRecipe recipe,
            OverloadProcessingFactoryInventory inventory,
            long availableHighVoltage,
            long availableExtremeHighVoltage) {
        if (recipe.lightningCost() <= 0) {
            return 0;
        }

        return maxLightningParallel(
                recipe.lightningTier(),
                recipe.lightningCost(),
                inventory.hasLightningCollapseMatrix(),
                availableHighVoltage,
                availableExtremeHighVoltage);
    }

    static int maxLightningParallel(
            LightningKey.Tier lightningTier,
            int lightningCost,
            boolean hasLightningCollapseMatrix,
            long availableHighVoltage,
            long availableExtremeHighVoltage) {
        if (lightningCost <= 0 || availableHighVoltage < 0L || availableExtremeHighVoltage < 0L) {
            return 0;
        }
        if (lightningTier == LightningKey.Tier.HIGH_VOLTAGE) {
            return (int) Math.min(Integer.MAX_VALUE, availableHighVoltage / lightningCost);
        }

        long exactParallel = availableExtremeHighVoltage / lightningCost;
        if (!hasLightningCollapseMatrix) {
            return (int) Math.min(Integer.MAX_VALUE, exactParallel);
        }

        long remainingExtreme = availableExtremeHighVoltage % lightningCost;
        long equivalentCost = (long) lightningCost * EXTREME_TO_HIGH_RATIO;
        long additionalParallel = availableHighVoltage / equivalentCost;
        long remainingHighVoltage = availableHighVoltage % equivalentCost;
        if (remainingExtreme * EXTREME_TO_HIGH_RATIO + remainingHighVoltage >= equivalentCost) {
            additionalParallel++;
        }
        if (Long.MAX_VALUE - exactParallel < additionalParallel) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.min(Integer.MAX_VALUE, exactParallel + additionalParallel);
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
        return dividend / divisor + (dividend % divisor == 0L ? 0L : 1L);
    }

    private static SelectionKey selectionKey(RecipeHolder<OverloadProcessingRecipe> holder, int parallel) {
        var recipe = holder.value();
        return new SelectionKey(
                parallel,
                recipe.priority(),
                recipe.itemInputs().size(),
                recipe.totalInputCount(),
                holder.id());
    }

    private record SelectionKey(
            int parallel,
            int priority,
            int itemInputKinds,
            int totalInputCount,
            ResourceLocation recipeId) {
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
