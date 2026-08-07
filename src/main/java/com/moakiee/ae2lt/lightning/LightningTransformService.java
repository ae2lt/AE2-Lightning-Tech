package com.moakiee.ae2lt.lightning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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

    private static final Comparator<LightningTransformRecipe> RECIPE_ORDER = Comparator
            .comparingInt(LightningTransformRecipe::priority)
            .reversed()
            .thenComparing(Comparator.comparingInt(LightningTransformRecipe::ingredientCount).reversed())
            .thenComparing(Comparator.comparingInt(LightningTransformRecipe::totalInputCount).reversed())
            .thenComparing(recipe -> recipe.getId().toString());

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

        List<LightningTransformRecipe> sortedRecipes = getSortedRecipes(level);
        if (sortedRecipes.isEmpty()) {
            return;
        }

        List<LightningTransformPlan> executedPlans = new ArrayList<>();
        List<PendingOutput> pendingOutputs = new ArrayList<>();

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

            pendingOutputs.add(new PendingOutput(
                    matchedRecipe.get().recipe().getResultItem(level.registryAccess()),
                    plan.spawnPosition()));
            executedPlans.add(plan);
        }

        spawnAccumulatedResults(level, pendingOutputs, gameTime);

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

    private static List<LightningTransformRecipe> getSortedRecipes(ServerLevel level) {
        List<LightningTransformRecipe> recipes =
                new ArrayList<>(level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.LIGHTNING_TRANSFORM_TYPE.get()));
        recipes.sort(RECIPE_ORDER);
        return recipes;
    }

    private static Optional<MatchedRecipe> selectRecipe(
            List<LightningTransformRecipe> sortedRecipes, LightningTransformRecipeInput input) {
        for (LightningTransformRecipe recipe : sortedRecipes) {
            Optional<LightningTransformPlan> plan = recipe.planMatch(input);
            if (plan.isPresent()) {
                return Optional.of(new MatchedRecipe(recipe, plan.get()));
            }
        }

        return Optional.empty();
    }

    private static void spawnAccumulatedResults(ServerLevel level, List<PendingOutput> pendingOutputs, long gameTime) {
        if (pendingOutputs.isEmpty()) {
            return;
        }

        Map<ItemStackKey, AccumulatedOutput> accumulated = new LinkedHashMap<>();
        for (PendingOutput pending : pendingOutputs) {
            if (pending.result.isEmpty()) {
                continue;
            }
            ItemStackKey key = new ItemStackKey(pending.result);
            accumulated.computeIfAbsent(key, ignored -> new AccumulatedOutput(pending.result))
                    .add(pending.result.getCount(), pending.position);
        }

        for (AccumulatedOutput output : accumulated.values()) {
            Vec3 position = output.averagePosition();
            ItemStack remaining = output.stack.copyWithCount(output.totalCount);
            while (!remaining.isEmpty()) {
                int spawnCount = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                ItemStack spawnedStack = remaining.copyWithCount(spawnCount);
                remaining.shrink(spawnCount);

                ItemEntity itemEntity = new ItemEntity(
                        level, position.x, position.y, position.z, spawnedStack);
                itemEntity.setDeltaMovement(Vec3.ZERO);
                ProtectedItemEntityHelper.applyOutputProtection(itemEntity, gameTime);
                level.addFreshEntity(itemEntity);
            }
        }
    }

    private record PendingOutput(ItemStack result, Vec3 position) {
    }

    private record MatchedRecipe(LightningTransformRecipe recipe, LightningTransformPlan plan) {
    }

    private static final class ItemStackKey {
        private final ItemStack stack;
        private final int hash;

        private ItemStackKey(ItemStack stack) {
            this.stack = stack.copyWithCount(1);
            this.hash = Objects.hash(this.stack.getItem(), this.stack.getTag());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (!(other instanceof ItemStackKey itemStackKey)) {
                return false;
            }

            return ItemStack.isSameItemSameTags(this.stack, itemStackKey.stack);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class AccumulatedOutput {
        private final ItemStack stack;
        private int totalCount;
        private double totalX;
        private double totalY;
        private double totalZ;
        private int positionWeight;

        private AccumulatedOutput(ItemStack stack) {
            this.stack = stack.copyWithCount(1);
        }

        private void add(int count, Vec3 position) {
            totalCount += count;
            totalX += position.x * count;
            totalY += position.y * count;
            totalZ += position.z * count;
            positionWeight += count;
        }

        private Vec3 averagePosition() {
            return new Vec3(
                    totalX / positionWeight,
                    totalY / positionWeight,
                    totalZ / positionWeight);
        }
    }
}
