package com.moakiee.ae2lt.machine.lightningchamber.recipe;

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

import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.util.RecipeSerializationHelper;

public final class LightningSimulationRecipe implements Recipe<LightningSimulationRecipeInput> {
    public static final long MIN_TOTAL_ENERGY = 5L;
    public static final int DEFAULT_LIGHTNING_COST = 4;
    public static final LightningKey.Tier DEFAULT_LIGHTNING_TIER = LightningKey.Tier.HIGH_VOLTAGE;
    private final ResourceLocation id;
    private final int priority;
    private final List<LightningSimulationIngredient> inputs;
    private final ItemStack result;
    private final long totalEnergy;
    private final int lightningCost;
    private final LightningKey.Tier lightningTier;
    private final int totalInputCount;

    public LightningSimulationRecipe(
            ResourceLocation id,
            int priority,
            List<LightningSimulationIngredient> inputs,
            ItemStack result,
            long totalEnergy,
            int lightningCost,
            LightningKey.Tier lightningTier) {
        this.id = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(lightningTier, "lightningTier");
        if (inputs.isEmpty() || inputs.size() > 3) {
            throw new IllegalArgumentException("inputs must contain 1 to 3 entries");
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("result cannot be empty");
        }
        if (totalEnergy < MIN_TOTAL_ENERGY) {
            throw new IllegalArgumentException("totalEnergy must be at least " + MIN_TOTAL_ENERGY);
        }
        if (lightningCost <= 0) {
            throw new IllegalArgumentException("lightningCost must be positive");
        }

        this.priority = priority;
        this.inputs = List.copyOf(inputs);
        this.result = result.copy();
        this.totalEnergy = totalEnergy;
        this.lightningCost = lightningCost;
        this.lightningTier = lightningTier;
        this.totalInputCount = this.inputs.stream().mapToInt(LightningSimulationIngredient::count).sum();
    }

    public int priority() {
        return priority;
    }

    public List<LightningSimulationIngredient> inputs() {
        return inputs;
    }

    public ItemStack getResultStack() {
        return result.copy();
    }

    public long totalEnergy() {
        return totalEnergy;
    }

    public int lightningCost() {
        return lightningCost;
    }

    public LightningKey.Tier lightningTier() {
        return lightningTier;
    }

    public int totalInputCount() {
        return totalInputCount;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public boolean matches(LightningSimulationRecipeInput input, Level level) {
        return planMatch(input).isPresent();
    }

    public Optional<LightningSimulationRecipeMatch> planMatch(LightningSimulationRecipeInput input) {
        List<LightningSimulationRecipeInput.SlotStack> slotStacks = input.slotStacks();
        if (slotStacks.isEmpty() || slotStacks.size() > 3) {
            return Optional.empty();
        }

        int[] slotFlexibility = new int[slotStacks.size()];
        List<List<Integer>> rawMatches = new ArrayList<>(inputs.size());

        for (LightningSimulationIngredient requirement : inputs) {
            List<Integer> matchingSlots = new ArrayList<>();
            int availableCount = 0;

            for (int slotIndex = 0; slotIndex < slotStacks.size(); slotIndex++) {
                var slotStack = slotStacks.get(slotIndex);
                if (!requirement.ingredient().test(slotStack.stack())) {
                    continue;
                }

                matchingSlots.add(slotIndex);
                availableCount += slotStack.stack().getCount();
                slotFlexibility[slotIndex]++;
            }

            if (availableCount < requirement.count()) {
                return Optional.empty();
            }

            rawMatches.add(matchingSlots);
        }

        List<RequirementState> requirements = new ArrayList<>(inputs.size());
        for (int requirementIndex = 0; requirementIndex < inputs.size(); requirementIndex++) {
            LightningSimulationIngredient requirement = inputs.get(requirementIndex);
            List<Integer> matchingSlots = rawMatches.get(requirementIndex);
            matchingSlots.sort(Comparator
                    .comparingInt((Integer slotIndex) -> slotFlexibility[slotIndex])
                    .thenComparing(Comparator.comparingInt(
                            (Integer slotIndex) -> slotStacks.get(slotIndex).stack().getCount()).reversed()));
            requirements.add(new RequirementState(
                    requirement.count(),
                    matchingSlots.stream().mapToInt(Integer::intValue).toArray()));
        }

        requirements.sort(Comparator
                .comparingInt(RequirementState::matchingSlotCount)
                .thenComparing(Comparator.comparingInt(RequirementState::count).reversed()));

        int[] remainingCounts = slotStacks.stream().mapToInt(slotStack -> slotStack.stack().getCount()).toArray();
        int[] slotConsumptions = new int[3];

        if (!allocateRequirement(0, requirements, slotStacks, remainingCounts, slotConsumptions)) {
            return Optional.empty();
        }

        return Optional.of(new LightningSimulationRecipeMatch(slotConsumptions));
    }

    @Override
    public ItemStack assemble(LightningSimulationRecipeInput input, RegistryAccess registries) {
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
        for (var input : inputs) {
            ingredients.add(input.ingredient());
        }
        return ingredients;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.LIGHTNING_SIMULATION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get();
    }

    @Override
    public boolean isIncomplete() {
        return inputs.isEmpty()
                || result.isEmpty()
                || totalEnergy < MIN_TOTAL_ENERGY
                || lightningCost <= 0
                || inputs.stream().anyMatch(input -> input.ingredient().getItems().length == 0);
    }

    private ItemStack rawResult() {
        return result;
    }

    private boolean allocateRequirement(
            int requirementIndex,
            List<RequirementState> requirements,
            List<LightningSimulationRecipeInput.SlotStack> slotStacks,
            int[] remainingCounts,
            int[] slotConsumptions) {
        if (requirementIndex >= requirements.size()) {
            return true;
        }

        RequirementState requirement = requirements.get(requirementIndex);
        return allocateAcrossSlots(
                requirementIndex,
                requirements,
                requirement,
                slotStacks,
                0,
                requirement.count(),
                remainingCounts,
                slotConsumptions);
    }

    private boolean allocateAcrossSlots(
            int requirementIndex,
            List<RequirementState> requirements,
            RequirementState requirement,
            List<LightningSimulationRecipeInput.SlotStack> slotStacks,
            int slotCursor,
            int needed,
            int[] remainingCounts,
            int[] slotConsumptions) {
        if (needed == 0) {
            return allocateRequirement(requirementIndex + 1, requirements, slotStacks, remainingCounts, slotConsumptions);
        }
        if (slotCursor >= requirement.matchingSlots.length) {
            return false;
        }
        if (remainingCapacity(requirement.matchingSlots, slotCursor, remainingCounts) < needed) {
            return false;
        }

        int slotIndex = requirement.matchingSlots[slotCursor];
        int maxTake = Math.min(needed, remainingCounts[slotIndex]);
        int machineSlot = slotStacks.get(slotIndex).slot();

        for (int take = maxTake; take >= 0; take--) {
            if (take > 0) {
                remainingCounts[slotIndex] -= take;
                slotConsumptions[machineSlot] += take;
            }

            if (allocateAcrossSlots(
                    requirementIndex,
                    requirements,
                    requirement,
                    slotStacks,
                    slotCursor + 1,
                    needed - take,
                    remainingCounts,
                    slotConsumptions)) {
                return true;
            }

            if (take > 0) {
                slotConsumptions[machineSlot] -= take;
                remainingCounts[slotIndex] += take;
            }
        }

        return false;
    }

    private int remainingCapacity(int[] matchingSlots, int startIndex, int[] remainingCounts) {
        int total = 0;
        for (int index = startIndex; index < matchingSlots.length; index++) {
            total += remainingCounts[matchingSlots[index]];
        }
        return total;
    }

    private static final class RequirementState {
        private final int count;
        private final int[] matchingSlots;

        private RequirementState(int count, int[] matchingSlots) {
            this.count = count;
            this.matchingSlots = matchingSlots;
        }

        private int count() {
            return count;
        }

        private int matchingSlotCount() {
            return matchingSlots.length;
        }
    }

    public static final class Serializer implements RecipeSerializer<LightningSimulationRecipe> {
        @Override
        public LightningSimulationRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            JsonArray inputsJson = GsonHelper.getAsJsonArray(json, "inputs");
            List<LightningSimulationIngredient> inputs = new ArrayList<>(inputsJson.size());
            for (var element : inputsJson) {
                inputs.add(LightningSimulationIngredient.fromJson(GsonHelper.convertToJsonObject(element, "inputs[]")));
            }

            return new LightningSimulationRecipe(
                    recipeId,
                    GsonHelper.getAsInt(json, "priority", 0),
                    inputs,
                    RecipeSerializationHelper.itemStackFromJson(json, "result"),
                    GsonHelper.getAsLong(json, "totalEnergy"),
                    GsonHelper.getAsInt(json, "lightningCost", DEFAULT_LIGHTNING_COST),
                    RecipeSerializationHelper.enumFromJson(
                            json,
                            "lightningTier",
                            DEFAULT_LIGHTNING_TIER,
                            LightningKey.Tier.values()));
        }

        @Override
        public LightningSimulationRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            int inputCount = buffer.readInt();
            List<LightningSimulationIngredient> inputs = new ArrayList<>(inputCount);
            for (int i = 0; i < inputCount; i++) {
                inputs.add(LightningSimulationIngredient.fromNetwork(buffer));
            }

            return new LightningSimulationRecipe(
                    recipeId,
                    buffer.readInt(),
                    inputs,
                    buffer.readItem(),
                    buffer.readLong(),
                    buffer.readInt(),
                    buffer.readEnum(LightningKey.Tier.class));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, LightningSimulationRecipe recipe) {
            buffer.writeInt(recipe.inputs().size());
            for (LightningSimulationIngredient input : recipe.inputs()) {
                input.toNetwork(buffer);
            }
            buffer.writeInt(recipe.priority());
            buffer.writeItem(recipe.rawResult());
            buffer.writeLong(recipe.totalEnergy());
            buffer.writeInt(recipe.lightningCost());
            buffer.writeEnum(recipe.lightningTier());
        }
    }
}
