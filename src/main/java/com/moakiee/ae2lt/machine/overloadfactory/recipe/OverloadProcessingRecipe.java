package com.moakiee.ae2lt.machine.overloadfactory.recipe;

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
import net.minecraftforge.fluids.FluidStack;

import com.moakiee.ae2lt.logic.FluidStackHelper;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.util.RecipeSerializationHelper;

public final class OverloadProcessingRecipe implements Recipe<OverloadProcessingRecipeInput> {
    public static final long MIN_TOTAL_ENERGY = 5L;
    public static final int DEFAULT_LIGHTNING_COST = 4;
    public static final LightningKey.Tier DEFAULT_LIGHTNING_TIER = LightningKey.Tier.HIGH_VOLTAGE;
    private final ResourceLocation id;
    private final int priority;
    private final List<OverloadProcessingIngredient> itemInputs;
    private final FluidStack fluidInput;
    private final List<ItemStack> itemResults;
    private final FluidStack fluidResult;
    private final long totalEnergy;
    private final int lightningCost;
    private final LightningKey.Tier lightningTier;
    private final int totalInputCount;

    public OverloadProcessingRecipe(
            ResourceLocation id,
            int priority,
            List<OverloadProcessingIngredient> itemInputs,
            FluidStack fluidInput,
            List<ItemStack> itemResults,
            FluidStack fluidResult,
            long totalEnergy,
            int lightningCost,
            LightningKey.Tier lightningTier) {
        this.id = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(itemInputs, "itemInputs");
        Objects.requireNonNull(fluidInput, "fluidInput");
        Objects.requireNonNull(itemResults, "itemResults");
        Objects.requireNonNull(fluidResult, "fluidResult");
        Objects.requireNonNull(lightningTier, "lightningTier");
        if (itemInputs.size() > OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT) {
            throw new IllegalArgumentException("itemInputs must contain at most 9 entries");
        }
        if (itemResults.size() > OverloadProcessingFactoryInventory.OUTPUT_SLOT_COUNT) {
            throw new IllegalArgumentException("itemResults must contain at most 1 entry");
        }
        if (itemInputs.isEmpty() && fluidInput.isEmpty()) {
            throw new IllegalArgumentException("recipe must define at least one item or fluid input");
        }
        if (itemResults.isEmpty() && fluidResult.isEmpty()) {
            throw new IllegalArgumentException("recipe must define at least one item or fluid output");
        }
        if (itemResults.stream().anyMatch(ItemStack::isEmpty)) {
            throw new IllegalArgumentException("itemResults cannot contain empty stacks");
        }
        if (totalEnergy < MIN_TOTAL_ENERGY) {
            throw new IllegalArgumentException("totalEnergy must be at least " + MIN_TOTAL_ENERGY);
        }
        if (lightningCost <= 0) {
            throw new IllegalArgumentException("lightningCost must be positive");
        }

        this.priority = priority;
        this.itemInputs = List.copyOf(itemInputs);
        this.fluidInput = fluidInput.copy();
        this.itemResults = itemResults.stream().map(ItemStack::copy).toList();
        this.fluidResult = fluidResult.copy();
        this.totalEnergy = totalEnergy;
        this.lightningCost = lightningCost;
        this.lightningTier = lightningTier;
        this.totalInputCount = this.itemInputs.stream().mapToInt(OverloadProcessingIngredient::count).sum();
    }

    public int priority() {
        return priority;
    }

    public List<OverloadProcessingIngredient> itemInputs() {
        return itemInputs;
    }

    public FluidStack fluidInput() {
        return fluidInput.copy();
    }

    public List<ItemStack> itemResults() {
        return itemResults.stream().map(ItemStack::copy).toList();
    }

    public FluidStack fluidResult() {
        return fluidResult.copy();
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
    public boolean matches(OverloadProcessingRecipeInput input, Level level) {
        return planMatch(input, 1).isPresent() && hasRequiredFluid(input.inputFluid(), 1);
    }

    public Optional<OverloadProcessingRecipeMatch> planMatch(OverloadProcessingRecipeInput input, int operations) {
        if (operations <= 0 || input == null) {
            return Optional.empty();
        }
        return prepareMatch(input).flatMap(plan -> plan.allocate(operations));
    }

    /**
     * Runs the operation-independent part of matching once: ingredient/slot
     * pairing, per-requirement availability and requirement ordering. The
     * returned plan can then cheaply allocate for any operation count, so
     * parallel search does not re-run Ingredient.test per probe.
     */
    public Optional<MatchPlan> prepareMatch(OverloadProcessingRecipeInput input) {
        if (input == null) {
            return Optional.empty();
        }

        List<OverloadProcessingRecipeInput.SlotStack> slotStacks = input.slotStacks();
        if (itemInputs.isEmpty()) {
            if (!slotStacks.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new MatchPlan(slotStacks, List.of()));
        }

        if (slotStacks.isEmpty() || slotStacks.size() > OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT) {
            return Optional.empty();
        }

        List<PreparedRequirement> requirements = new ArrayList<>(itemInputs.size());
        for (OverloadProcessingIngredient requirement : itemInputs) {
            List<Integer> matchingSlots = new ArrayList<>();
            long availableCount = 0L;

            for (int slotIndex = 0; slotIndex < slotStacks.size(); slotIndex++) {
                var slotStack = slotStacks.get(slotIndex);
                if (!requirement.ingredient().test(slotStack.stack())) {
                    continue;
                }

                matchingSlots.add(slotIndex);
                availableCount += slotStack.stack().getCount();
            }

            if (availableCount < requirement.count()) {
                return Optional.empty();
            }

            // When multiple slots can satisfy the same ingredient, prefer the
            // machine's natural top-to-bottom slot order so repeated crafts
            // drain upper rows before lower ones.
            matchingSlots.sort(Comparator
                    .comparingInt((Integer slotIndex) -> slotStacks.get(slotIndex).slot()));
            int[] matchingSlotArray = new int[matchingSlots.size()];
            for (int index = 0; index < matchingSlots.size(); index++) {
                matchingSlotArray[index] = matchingSlots.get(index);
            }
            requirements.add(new PreparedRequirement(
                    requirement.count(),
                    matchingSlotArray,
                    availableCount));
        }

        // Same ordering the scaled matcher used: fewest candidate slots first,
        // larger demand first. Scaling by operations preserves this order.
        requirements.sort(Comparator
                .comparingInt((PreparedRequirement requirement) -> requirement.matchingSlots().length)
                .thenComparing(Comparator.comparingInt(PreparedRequirement::baseCount).reversed()));

        return Optional.of(new MatchPlan(slotStacks, requirements));
    }

    public boolean hasRequiredFluid(FluidStack availableFluid, int operations) {
        if (operations <= 0) {
            return false;
        }
        if (fluidInput.isEmpty()) {
            return true;
        }
        return !availableFluid.isEmpty()
                && FluidStackHelper.sameFluidAndTag(fluidInput, availableFluid)
                && availableFluid.getAmount() >= multiplyExactToInt(fluidInput.getAmount(), operations);
    }

    public List<ItemStack> getScaledItemResults(int operations) {
        return itemResults.stream()
                .map(stack -> stack.copyWithCount(multiplyExactToInt(stack.getCount(), operations)))
                .toList();
    }

    public FluidStack getScaledFluidResult(int operations) {
        if (fluidResult.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(fluidResult, multiplyExactToInt(fluidResult.getAmount(), operations));
    }

    @Override
    public ItemStack assemble(OverloadProcessingRecipeInput input, RegistryAccess registries) {
        return itemResults.isEmpty() ? ItemStack.EMPTY : itemResults.get(0).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return itemResults.isEmpty() ? ItemStack.EMPTY : itemResults.get(0).copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (var input : itemInputs) {
            ingredients.add(input.ingredient());
        }
        return ingredients;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.OVERLOAD_PROCESSING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean isIncomplete() {
        return totalEnergy < MIN_TOTAL_ENERGY
                || lightningCost <= 0
                || (itemInputs.isEmpty() && fluidInput.isEmpty())
                || (itemResults.isEmpty() && fluidResult.isEmpty())
                || itemInputs.stream().anyMatch(input -> input.ingredient().getItems().length == 0);
    }

    private FluidStack rawFluidInput() {
        return fluidInput;
    }

    List<ItemStack> rawItemResults() {
        return itemResults;
    }

    FluidStack rawFluidResult() {
        return fluidResult;
    }

    private boolean allocateRequirement(
            int requirementIndex,
            List<PreparedRequirement> requirements,
            List<OverloadProcessingRecipeInput.SlotStack> slotStacks,
            int operations,
            int[] remainingCounts,
            int[] slotConsumptions) {
        if (requirementIndex >= requirements.size()) {
            return true;
        }

        PreparedRequirement requirement = requirements.get(requirementIndex);
        return allocateAcrossSlots(
                requirementIndex,
                requirements,
                requirement,
                slotStacks,
                operations,
                0,
                requirement.baseCount() * operations,
                remainingCounts,
                slotConsumptions);
    }

    private boolean allocateAcrossSlots(
            int requirementIndex,
            List<PreparedRequirement> requirements,
            PreparedRequirement requirement,
            List<OverloadProcessingRecipeInput.SlotStack> slotStacks,
            int operations,
            int slotCursor,
            int needed,
            int[] remainingCounts,
            int[] slotConsumptions) {
        if (needed == 0) {
            return allocateRequirement(
                    requirementIndex + 1, requirements, slotStacks, operations, remainingCounts, slotConsumptions);
        }
        if (slotCursor >= requirement.matchingSlots().length) {
            return false;
        }
        if (remainingCapacity(requirement.matchingSlots(), slotCursor, remainingCounts) < needed) {
            return false;
        }

        int slotIndex = requirement.matchingSlots()[slotCursor];
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
                    operations,
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

    private static int multiplyExactToInt(int value, int multiplier) {
        long result = (long) value * multiplier;
        if (result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("scaled stack size exceeds integer range");
        }
        return (int) result;
    }

    private record PreparedRequirement(int baseCount, int[] matchingSlots, long available) {
    }

    /**
     * Operation-independent match state for one recipe against one input
     * snapshot. Slot pairing and requirement order are fixed; only the
     * per-operation counts are scaled inside {@link #allocate(int)}.
     */
    public final class MatchPlan {
        private final List<OverloadProcessingRecipeInput.SlotStack> slotStacks;
        private final List<PreparedRequirement> requirements;

        private MatchPlan(
                List<OverloadProcessingRecipeInput.SlotStack> slotStacks,
                List<PreparedRequirement> requirements) {
            this.slotStacks = slotStacks;
            this.requirements = requirements;
        }

        /**
         * Optimistic upper bound from per-requirement availability. Slots
         * shared between requirements may reduce the real maximum, which
         * {@link #allocate(int)} decides exactly.
         */
        public long maxOperationsByAvailability() {
            long bound = Long.MAX_VALUE;
            for (PreparedRequirement requirement : requirements) {
                bound = Math.min(bound, requirement.available() / requirement.baseCount());
            }
            return bound;
        }

        public Optional<OverloadProcessingRecipeMatch> allocate(int operations) {
            if (operations <= 0) {
                return Optional.empty();
            }

            int[] slotConsumptions = new int[OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT];
            if (requirements.isEmpty()) {
                return Optional.of(new OverloadProcessingRecipeMatch(slotConsumptions));
            }

            for (PreparedRequirement requirement : requirements) {
                if (requirement.available() < (long) requirement.baseCount() * operations) {
                    return Optional.empty();
                }
            }

            int[] remainingCounts = new int[slotStacks.size()];
            for (int slotIndex = 0; slotIndex < remainingCounts.length; slotIndex++) {
                remainingCounts[slotIndex] = slotStacks.get(slotIndex).stack().getCount();
            }

            if (!allocateRequirement(0, requirements, slotStacks, operations, remainingCounts, slotConsumptions)) {
                return Optional.empty();
            }

            return Optional.of(new OverloadProcessingRecipeMatch(slotConsumptions));
        }
    }

    public static final class Serializer implements RecipeSerializer<OverloadProcessingRecipe> {
        @Override
        public OverloadProcessingRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            List<OverloadProcessingIngredient> itemInputs = new ArrayList<>();
            if (json.has("inputs")) {
                JsonArray inputsJson = GsonHelper.getAsJsonArray(json, "inputs");
                itemInputs = new ArrayList<>(inputsJson.size());
                for (var element : inputsJson) {
                    itemInputs.add(OverloadProcessingIngredient.fromJson(
                            GsonHelper.convertToJsonObject(element, "inputs[]")));
                }
            }

            List<ItemStack> itemResults = new ArrayList<>();
            if (json.has("results")) {
                JsonArray resultsJson = GsonHelper.getAsJsonArray(json, "results");
                itemResults = new ArrayList<>(resultsJson.size());
                for (var element : resultsJson) {
                    itemResults.add(RecipeSerializationHelper.itemStackFromJson(
                            GsonHelper.convertToJsonObject(element, "results[]")));
                }
            }

            return new OverloadProcessingRecipe(
                    recipeId,
                    GsonHelper.getAsInt(json, "priority", 0),
                    itemInputs,
                    RecipeSerializationHelper.optionalFluidStackFromJson(json, "inputFluid"),
                    itemResults,
                    RecipeSerializationHelper.optionalFluidStackFromJson(json, "resultFluid"),
                    GsonHelper.getAsLong(json, "totalEnergy"),
                    GsonHelper.getAsInt(json, "lightningCost", DEFAULT_LIGHTNING_COST),
                    RecipeSerializationHelper.enumFromJson(
                            json,
                            "lightningTier",
                            DEFAULT_LIGHTNING_TIER,
                            LightningKey.Tier.values()));
        }

        @Override
        public OverloadProcessingRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            int inputCount = buffer.readInt();
            List<OverloadProcessingIngredient> itemInputs = new ArrayList<>(inputCount);
            for (int i = 0; i < inputCount; i++) {
                itemInputs.add(OverloadProcessingIngredient.fromNetwork(buffer));
            }

            FluidStack inputFluid = buffer.readBoolean() ? buffer.readFluidStack() : FluidStack.EMPTY;

            int resultCount = buffer.readInt();
            List<ItemStack> itemResults = new ArrayList<>(resultCount);
            for (int i = 0; i < resultCount; i++) {
                itemResults.add(buffer.readItem());
            }

            FluidStack resultFluid = buffer.readBoolean() ? buffer.readFluidStack() : FluidStack.EMPTY;

            return new OverloadProcessingRecipe(
                    recipeId,
                    buffer.readInt(),
                    itemInputs,
                    inputFluid,
                    itemResults,
                    resultFluid,
                    buffer.readLong(),
                    buffer.readInt(),
                    buffer.readEnum(LightningKey.Tier.class));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, OverloadProcessingRecipe recipe) {
            buffer.writeInt(recipe.itemInputs().size());
            for (OverloadProcessingIngredient input : recipe.itemInputs()) {
                input.toNetwork(buffer);
            }

            FluidStack inputFluid = recipe.rawFluidInput();
            buffer.writeBoolean(!inputFluid.isEmpty());
            if (!inputFluid.isEmpty()) {
                buffer.writeFluidStack(inputFluid);
            }

            buffer.writeInt(recipe.rawItemResults().size());
            for (ItemStack itemResult : recipe.rawItemResults()) {
                buffer.writeItem(itemResult);
            }

            FluidStack resultFluid = recipe.rawFluidResult();
            buffer.writeBoolean(!resultFluid.isEmpty());
            if (!resultFluid.isEmpty()) {
                buffer.writeFluidStack(resultFluid);
            }

            buffer.writeInt(recipe.priority());
            buffer.writeLong(recipe.totalEnergy());
            buffer.writeInt(recipe.lightningCost());
            buffer.writeEnum(recipe.lightningTier());
        }
    }
}

