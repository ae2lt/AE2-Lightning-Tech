package com.moakiee.ae2lt.lightning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.registry.ModRecipeTypes;

public final class LightningTransformService {
    /**
     * Hard cap on transform rounds per single lightning bolt. Existing recipes peak at a
     * couple of rounds per strike; this cap exists purely to bound the worst case when an
     * exotic recipe set or a huge ItemEntity pile would otherwise let the loop spin
     * pathologically. Bumping this only matters if a legitimate single-strike pipeline
     * ever needs more than this many sequential transforms, which has not been observed.
     */
    private static final int MAX_ROUNDS_PER_STRIKE = 32;

    private static final Comparator<RecipeHolder<LightningTransformRecipe>> RECIPE_ORDER = Comparator
            .<RecipeHolder<LightningTransformRecipe>>comparingInt(holder -> holder.value().priority())
            .reversed()
            .thenComparing(Comparator.comparingInt(
                            (RecipeHolder<LightningTransformRecipe> holder) -> holder.value().ingredientCount())
                    .reversed())
            .thenComparing(Comparator.comparingInt(
                            (RecipeHolder<LightningTransformRecipe> holder) -> holder.value().totalInputCount())
                    .reversed())
            .thenComparing(holder -> holder.id().toString());

    private LightningTransformService() {
    }

    public static void handleLightning(ServerLevel level, LightningBolt lightningBolt) {
        long gameTime = level.getGameTime();
        // Snapshot the candidate pool exactly once. Newly spawned outputs receive
        // PROTECT_UNTIL_TAG via applyOutputProtection and would be filtered by
        // canParticipateInTransform anyway, so re-running the AABB scan every round only
        // burns CPU on a fixed set of candidates whose state we already have references to.
        List<ItemEntity> candidatePool = collectCandidates(level, lightningBolt.position(), gameTime);
        if (candidatePool.isEmpty()) {
            return;
        }

        List<RecipeHolder<LightningTransformRecipe>> sortedRecipes = getSortedRecipes(level);
        if (sortedRecipes.isEmpty()) {
            return;
        }

        List<LightningTransformPlan> executedPlans = new ArrayList<>();

        for (int round = 0; round < MAX_ROUNDS_PER_STRIKE; round++) {
            List<ItemEntity> candidates = filterStillEligible(candidatePool, gameTime);
            if (candidates.isEmpty()) {
                break;
            }

            LightningTransformRecipeInput input = LightningTransformRecipeInput.fromEntities(candidates);
            if (input.size() == 0) {
                break;
            }

            Optional<MatchedRecipe> matchedRecipe = selectRecipe(sortedRecipes, input);
            if (matchedRecipe.isEmpty()) {
                break;
            }

            LightningTransformPlan plan = matchedRecipe.get().plan();
            if (!plan.consumeInputs(gameTime)) {
                break;
            }

            spawnResult(
                    level,
                    matchedRecipe.get().recipe().value().getResultItem(level.registryAccess()),
                    plan.spawnPosition(),
                    gameTime);
            executedPlans.add(plan);
        }

        for (LightningTransformPlan plan : executedPlans) {
            plan.applyTransformLocks(gameTime);
        }
    }

    private static List<ItemEntity> collectCandidates(ServerLevel level, Vec3 lightningPosition, long gameTime) {
        AABB searchBox = new AABB(lightningPosition, lightningPosition).inflate(
                LightningTransformRules.SEARCH_HORIZONTAL_RADIUS,
                LightningTransformRules.SEARCH_VERTICAL_RADIUS,
                LightningTransformRules.SEARCH_HORIZONTAL_RADIUS);
        return level.getEntitiesOfClass(
                ItemEntity.class,
                searchBox,
                itemEntity -> ProtectedItemEntityHelper.canParticipateInTransform(itemEntity, gameTime));
    }

    /**
     * Filter the snapshotted pool to entities still eligible after prior rounds shrank or
     * discarded their stacks. This preserves the "one bolt may run multiple recipes against
     * partially-consumed stacks" behavior — locks are only applied once after the loop
     * completes, exactly as before.
     */
    private static List<ItemEntity> filterStillEligible(List<ItemEntity> pool, long gameTime) {
        List<ItemEntity> result = new ArrayList<>(pool.size());
        for (ItemEntity itemEntity : pool) {
            if (ProtectedItemEntityHelper.canParticipateInTransform(itemEntity, gameTime)) {
                result.add(itemEntity);
            }
        }
        return result;
    }

    private static List<RecipeHolder<LightningTransformRecipe>> getSortedRecipes(ServerLevel level) {
        List<RecipeHolder<LightningTransformRecipe>> recipes =
                new ArrayList<>(level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.LIGHTNING_TRANSFORM_TYPE.get()));
        recipes.sort(RECIPE_ORDER);
        return recipes;
    }

    private static Optional<MatchedRecipe> selectRecipe(
            List<RecipeHolder<LightningTransformRecipe>> sortedRecipes, LightningTransformRecipeInput input) {
        for (RecipeHolder<LightningTransformRecipe> recipeHolder : sortedRecipes) {
            Optional<LightningTransformPlan> plan = recipeHolder.value().planMatch(input);
            if (plan.isPresent()) {
                return Optional.of(new MatchedRecipe(recipeHolder, plan.get()));
            }
        }

        return Optional.empty();
    }

    private static void spawnResult(ServerLevel level, ItemStack result, Vec3 spawnPosition, long gameTime) {
        if (result.isEmpty()) {
            return;
        }

        ItemStack remaining = result.copy();
        while (!remaining.isEmpty()) {
            int spawnCount = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            ItemStack spawnedStack = remaining.copyWithCount(spawnCount);
            remaining.shrink(spawnCount);

            ItemEntity itemEntity = new ItemEntity(level, spawnPosition.x, spawnPosition.y, spawnPosition.z, spawnedStack);
            itemEntity.setDeltaMovement(Vec3.ZERO);
            ProtectedItemEntityHelper.applyOutputProtection(itemEntity, gameTime);
            level.addFreshEntity(itemEntity);
        }
    }

    private record MatchedRecipe(RecipeHolder<LightningTransformRecipe> recipe, LightningTransformPlan plan) {
    }
}
