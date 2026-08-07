package com.moakiee.ae2lt.lightning;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.registry.ModRecipeTypes;

import com.moakiee.ae2lt.util.RecipeSerializationHelper;

public final class LightningTransformRecipe implements Recipe<LightningTransformRecipeInput> {
    private final ResourceLocation id;
    private final int priority;
    private final List<CountedIngredient> inputs;
    private final ItemStack result;
    private final int totalInputCount;

    public LightningTransformRecipe(ResourceLocation id, int priority, List<CountedIngredient> inputs, ItemStack result) {
        this.id = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(result, "result");
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs cannot be empty");
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("result cannot be empty");
        }

        this.priority = priority;
        this.inputs = List.copyOf(inputs);
        this.result = result.copy();
        this.totalInputCount = this.inputs.stream().mapToInt(CountedIngredient::count).sum();
    }

    public int priority() {
        return priority;
    }

    public List<CountedIngredient> inputs() {
        return inputs;
    }

    public int ingredientCount() {
        return inputs.size();
    }

    public int totalInputCount() {
        return totalInputCount;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public boolean matches(LightningTransformRecipeInput input, Level level) {
        return planMatch(input).isPresent();
    }

    public Optional<LightningTransformPlan> planMatch(LightningTransformRecipeInput input) {
        List<LightningTransformRecipeInput.GroupedStack> groupedStacks = input.groupedStacks();
        if (groupedStacks.isEmpty()) {
            return Optional.empty();
        }

        int[] groupFlexibility = new int[groupedStacks.size()];
        List<List<Integer>> rawMatches = new ArrayList<>(inputs.size());

        for (CountedIngredient countedIngredient : inputs) {
            List<Integer> matchingGroups = new ArrayList<>();
            int availableCount = 0;

            for (int groupIndex = 0; groupIndex < groupedStacks.size(); groupIndex++) {
                LightningTransformRecipeInput.GroupedStack groupedStack = groupedStacks.get(groupIndex);
                if (!countedIngredient.ingredient().test(groupedStack.stack())) {
                    continue;
                }

                matchingGroups.add(groupIndex);
                availableCount += groupedStack.totalCount();
                groupFlexibility[groupIndex]++;
            }

            if (availableCount < countedIngredient.count()) {
                return Optional.empty();
            }

            rawMatches.add(matchingGroups);
        }

        List<RequirementState> requirements = new ArrayList<>(inputs.size());
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            CountedIngredient countedIngredient = inputs.get(inputIndex);
            List<Integer> matchingGroups = rawMatches.get(inputIndex);
            matchingGroups.sort(Comparator
                    .comparingInt((Integer groupIndex) -> groupFlexibility[groupIndex])
                    .thenComparing(Comparator.comparingInt(
                                    (Integer groupIndex) -> groupedStacks.get(groupIndex).totalCount())
                            .reversed()));
            requirements.add(new RequirementState(
                    countedIngredient.count(),
                    matchingGroups.stream().mapToInt(Integer::intValue).toArray()));
        }

        requirements.sort(Comparator
                .comparingInt(RequirementState::matchingGroupCount)
                .thenComparing(Comparator.comparingInt(RequirementState::count).reversed()));

        int[] remainingCounts = groupedStacks.stream().mapToInt(LightningTransformRecipeInput.GroupedStack::totalCount).toArray();
        int[] groupConsumptions = new int[groupedStacks.size()];

        // Backtracking keeps the recipe expressive even when tag ingredients overlap.
        if (!allocateRequirement(0, requirements, remainingCounts, groupConsumptions)) {
            return Optional.empty();
        }

        return Optional.of(LightningTransformPlan.fromGroupCounts(groupedStacks, groupConsumptions));
    }

    @Override
    public ItemStack assemble(LightningTransformRecipeInput input, RegistryAccess registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (CountedIngredient input : inputs) {
            ingredients.add(input.ingredient());
        }
        return ingredients;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.LIGHTNING_TRANSFORM_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.LIGHTNING_TRANSFORM_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean isIncomplete() {
        return inputs.isEmpty()
                || result.isEmpty()
                || inputs.stream().anyMatch(input -> input.ingredient().getItems().length == 0);
    }

    private ItemStack rawResult() {
        return result;
    }

    private boolean allocateRequirement(
            int requirementIndex,
            List<RequirementState> requirements,
            int[] remainingCounts,
            int[] groupConsumptions) {
        if (requirementIndex >= requirements.size()) {
            return true;
        }

        RequirementState requirement = requirements.get(requirementIndex);
        return allocateAcrossGroups(
                requirementIndex,
                requirements,
                requirement,
                0,
                requirement.count(),
                remainingCounts,
                groupConsumptions);
    }

    private boolean allocateAcrossGroups(
            int requirementIndex,
            List<RequirementState> requirements,
            RequirementState requirement,
            int groupCursor,
            int needed,
            int[] remainingCounts,
            int[] groupConsumptions) {
        if (needed == 0) {
            return allocateRequirement(requirementIndex + 1, requirements, remainingCounts, groupConsumptions);
        }

        if (groupCursor >= requirement.matchingGroups.length) {
            return false;
        }

        if (remainingCapacity(requirement.matchingGroups, groupCursor, remainingCounts) < needed) {
            return false;
        }

        int groupIndex = requirement.matchingGroups[groupCursor];
        int maxTake = Math.min(needed, remainingCounts[groupIndex]);

        for (int take = maxTake; take >= 0; take--) {
            if (take > 0) {
                remainingCounts[groupIndex] -= take;
                groupConsumptions[groupIndex] += take;
            }

            if (allocateAcrossGroups(
                    requirementIndex,
                    requirements,
                    requirement,
                    groupCursor + 1,
                    needed - take,
                    remainingCounts,
                    groupConsumptions)) {
                return true;
            }

            if (take > 0) {
                groupConsumptions[groupIndex] -= take;
                remainingCounts[groupIndex] += take;
            }
        }

        return false;
    }

    private int remainingCapacity(int[] matchingGroups, int startIndex, int[] remainingCounts) {
        int total = 0;
        for (int index = startIndex; index < matchingGroups.length; index++) {
            total += remainingCounts[matchingGroups[index]];
        }
        return total;
    }

    private static final class RequirementState {
        private final int count;
        private final int[] matchingGroups;

        private RequirementState(int count, int[] matchingGroups) {
            this.count = count;
            this.matchingGroups = matchingGroups;
        }

        private int count() {
            return count;
        }

        private int matchingGroupCount() {
            return matchingGroups.length;
        }
    }

    public static final class Serializer implements RecipeSerializer<LightningTransformRecipe> {
        @Override
        public LightningTransformRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            int priority = GsonHelper.getAsInt(json, "priority", 0);
            JsonArray inputsJson = GsonHelper.getAsJsonArray(json, "inputs");
            List<CountedIngredient> inputs = new ArrayList<>(inputsJson.size());
            for (var element : inputsJson) {
                inputs.add(CountedIngredient.fromJson(GsonHelper.convertToJsonObject(element, "inputs[]")));
            }

            return new LightningTransformRecipe(
                    recipeId,
                    priority,
                    inputs,
                    RecipeSerializationHelper.itemStackFromJson(json, "result"));
        }

        @Override
        public LightningTransformRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            int priority = buffer.readInt();
            int inputCount = buffer.readInt();
            List<CountedIngredient> inputs = new ArrayList<>(inputCount);
            for (int i = 0; i < inputCount; i++) {
                inputs.add(CountedIngredient.fromNetwork(buffer));
            }

            return new LightningTransformRecipe(recipeId, priority, inputs, buffer.readItem());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, LightningTransformRecipe recipe) {
            buffer.writeInt(recipe.priority());
            buffer.writeInt(recipe.inputs().size());
            for (CountedIngredient input : recipe.inputs()) {
                input.toNetwork(buffer);
            }
            buffer.writeItem(recipe.rawResult());
        }
    }
}
