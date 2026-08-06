package com.moakiee.ae2lt.machine.firmament.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.machine.firmament.FirmamentConversionInventory;
import com.moakiee.ae2lt.registry.ModRecipeTypes;

public final class FirmamentConversionRecipe implements Recipe<FirmamentConversionRecipeInput> {
    private static final Codec<List<FirmamentConversionIngredient>> INPUTS_CODEC =
            FirmamentConversionIngredient.CODEC.codec()
                    .listOf()
                    .validate(inputs -> {
                        if (inputs.isEmpty()) {
                            return DataResult.error(() -> "firmament conversion recipe inputs cannot be empty");
                        }
                        if (inputs.size() > 3) {
                            return DataResult.error(() -> "firmament conversion supports at most 3 inputs");
                        }
                        return DataResult.success(List.copyOf(inputs));
                    });
    private static final Codec<Integer> POSITIVE_PROCESS_TIME_CODEC =
            Codec.intRange(1, Integer.MAX_VALUE);
    private static final Codec<List<ItemStack>> OUTPUTS_CODEC = ItemStack.STRICT_CODEC.listOf().validate(results -> {
        if (results.isEmpty()) {
            return DataResult.error(() -> "firmament conversion recipe results cannot be empty");
        }
        if (results.size() > FirmamentConversionInventory.OUTPUT_SLOT_COUNT) {
            return DataResult.error(() -> "firmament conversion supports at most 4 results");
        }
        if (results.stream().anyMatch(ItemStack::isEmpty)) {
            return DataResult.error(() -> "firmament conversion results cannot contain empty stacks");
        }
        return DataResult.success(List.copyOf(results));
    });
    private static final StreamCodec<RegistryFriendlyByteBuf, List<FirmamentConversionIngredient>> INPUTS_STREAM_CODEC =
            FirmamentConversionIngredient.STREAM_CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> OUTPUTS_STREAM_CODEC =
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list());

    private final int priority;
    private final List<FirmamentConversionIngredient> inputs;
    private final List<ItemStack> results;
    private final int processTime;
    private final int totalInputCount;

    public FirmamentConversionRecipe(
            int priority,
            List<FirmamentConversionIngredient> inputs,
            ItemStack result,
            int processTime) {
        this(priority, inputs, List.of(result), processTime);
    }

    public FirmamentConversionRecipe(
            int priority,
            List<FirmamentConversionIngredient> inputs,
            List<ItemStack> results,
            int processTime) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(results, "results");
        if (inputs.isEmpty() || inputs.size() > 3) {
            throw new IllegalArgumentException("inputs must contain 1 to 3 entries");
        }
        if (results.isEmpty() || results.size() > FirmamentConversionInventory.OUTPUT_SLOT_COUNT) {
            throw new IllegalArgumentException("results must contain 1 to 4 entries");
        }
        if (results.stream().anyMatch(ItemStack::isEmpty)) {
            throw new IllegalArgumentException("results cannot contain empty stacks");
        }
        if (processTime <= 0) {
            throw new IllegalArgumentException("processTime must be positive");
        }

        this.priority = priority;
        this.inputs = List.copyOf(inputs);
        this.results = results.stream().map(ItemStack::copy).toList();
        this.processTime = processTime;
        this.totalInputCount = this.inputs.stream().mapToInt(FirmamentConversionIngredient::count).sum();
    }

    public int priority() {
        return priority;
    }

    public List<FirmamentConversionIngredient> inputs() {
        return inputs;
    }

    public ItemStack getResultStack() {
        return results.getFirst().copy();
    }

    public List<ItemStack> getResultStacks() {
        return results.stream().map(ItemStack::copy).toList();
    }

    public int processTime() {
        return processTime;
    }

    public int totalInputCount() {
        return totalInputCount;
    }

    @Override
    public boolean matches(FirmamentConversionRecipeInput input, Level level) {
        return planMatch(input).isPresent();
    }

    public Optional<FirmamentConversionRecipeMatch> planMatch(FirmamentConversionRecipeInput input) {
        List<FirmamentConversionRecipeInput.SlotStack> slotStacks = input.slotStacks();
        if (slotStacks.isEmpty() || slotStacks.size() > 3) {
            return Optional.empty();
        }

        int[] slotFlexibility = new int[slotStacks.size()];
        List<List<Integer>> rawMatches = new ArrayList<>(inputs.size());

        for (FirmamentConversionIngredient requirement : inputs) {
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
            FirmamentConversionIngredient requirement = inputs.get(requirementIndex);
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

        return Optional.of(new FirmamentConversionRecipeMatch(slotConsumptions));
    }

    @Override
    public ItemStack assemble(FirmamentConversionRecipeInput input, HolderLookup.Provider registries) {
        return getResultStack();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return getResultStack();
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
        return ModRecipeTypes.FIRMAMENT_CONVERSION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.FIRMAMENT_CONVERSION_TYPE.get();
    }

    @Override
    public boolean isIncomplete() {
        return inputs.isEmpty()
                || results.isEmpty()
                || results.stream().anyMatch(ItemStack::isEmpty)
                || processTime <= 0
                || inputs.stream().anyMatch(input -> input.ingredient().hasNoItems());
    }

    private List<ItemStack> rawResults() {
        return results;
    }

    private boolean allocateRequirement(
            int requirementIndex,
            List<RequirementState> requirements,
            List<FirmamentConversionRecipeInput.SlotStack> slotStacks,
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
            List<FirmamentConversionRecipeInput.SlotStack> slotStacks,
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

    public static final class Serializer implements RecipeSerializer<FirmamentConversionRecipe> {
        private static final MapCodec<FirmamentConversionRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.INT.optionalFieldOf("priority", 0).forGetter(FirmamentConversionRecipe::priority),
                        INPUTS_CODEC.fieldOf("inputs").forGetter(FirmamentConversionRecipe::inputs),
                        OUTPUTS_CODEC.fieldOf("results").forGetter(FirmamentConversionRecipe::rawResults),
                        POSITIVE_PROCESS_TIME_CODEC.fieldOf("processTime").forGetter(FirmamentConversionRecipe::processTime))
                .apply(instance, FirmamentConversionRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, FirmamentConversionRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT,
                        FirmamentConversionRecipe::priority,
                        INPUTS_STREAM_CODEC,
                        FirmamentConversionRecipe::inputs,
                        OUTPUTS_STREAM_CODEC,
                        FirmamentConversionRecipe::rawResults,
                        ByteBufCodecs.VAR_INT,
                        FirmamentConversionRecipe::processTime,
                        FirmamentConversionRecipe::new);

        @Override
        public MapCodec<FirmamentConversionRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FirmamentConversionRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
