package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModRecipeTypes;

public final class OverloadProcessingRecipe implements Recipe<OverloadProcessingRecipeInput> {
    public static final long MIN_TOTAL_ENERGY = 5L;
    public static final int DEFAULT_LIGHTNING_COST = 4;
    public static final LightningKey.Tier DEFAULT_LIGHTNING_TIER = LightningKey.Tier.HIGH_VOLTAGE;

    private static final Codec<List<OverloadProcessingIngredient>> INPUTS_CODEC =
            OverloadProcessingIngredient.CODEC.codec()
                    .listOf()
                    .validate(inputs -> inputs.size() > OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT
                            ? DataResult.error(() -> "overload processing supports at most 9 item inputs")
                            : DataResult.success(List.copyOf(inputs)));

    private static final Codec<List<ItemStackTemplate>> OUTPUTS_CODEC = ItemStackTemplate.CODEC.listOf().validate(outputs -> {
        if (outputs.size() > OverloadProcessingFactoryInventory.OUTPUT_SLOT_COUNT) {
            return DataResult.error(() -> "overload processing supports at most 1 item output");
        }
        return DataResult.success(List.copyOf(outputs));
    });

    private static final Codec<Long> POSITIVE_ENERGY_CODEC = Codec.LONG.validate(totalEnergy -> {
        if (totalEnergy < MIN_TOTAL_ENERGY) {
            return DataResult.error(() -> "totalEnergy must be at least " + MIN_TOTAL_ENERGY);
        }
        return DataResult.success(totalEnergy);
    });

    private static final Codec<Integer> POSITIVE_LIGHTNING_COST_CODEC = Codec.INT.validate(lightningCost -> {
        if (lightningCost <= 0) {
            return DataResult.error(() -> "lightningCost must be positive");
        }
        return DataResult.success(lightningCost);
    });

    public static final StreamCodec<RegistryFriendlyByteBuf, List<OverloadProcessingIngredient>> INPUTS_STREAM_CODEC =
            OverloadProcessingIngredient.STREAM_CODEC.apply(ByteBufCodecs.list());
    public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStackTemplate>> OUTPUTS_STREAM_CODEC =
            ItemStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list());
    public static final StreamCodec<RegistryFriendlyByteBuf, LightningKey.Tier> TIER_STREAM_CODEC =
            StreamCodec.of((buffer, tier) -> buffer.writeEnum(tier), buffer -> buffer.readEnum(LightningKey.Tier.class));

    private final int priority;
    private final List<OverloadProcessingIngredient> itemInputs;
    private final OverloadProcessingFluidStackTemplate fluidInput;
    private final List<ItemStackTemplate> itemResults;
    private final OverloadProcessingFluidStackTemplate fluidResult;
    private final long totalEnergy;
    private final int lightningCost;
    private final LightningKey.Tier lightningTier;
    private final int totalInputCount;

    public OverloadProcessingRecipe(
            int priority,
            List<OverloadProcessingIngredient> itemInputs,
            OverloadProcessingFluidStackTemplate fluidInput,
            List<ItemStackTemplate> itemResults,
            OverloadProcessingFluidStackTemplate fluidResult,
            long totalEnergy,
            int lightningCost,
            LightningKey.Tier lightningTier) {
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
        if (totalEnergy < MIN_TOTAL_ENERGY) {
            throw new IllegalArgumentException("totalEnergy must be at least " + MIN_TOTAL_ENERGY);
        }
        if (lightningCost <= 0) {
            throw new IllegalArgumentException("lightningCost must be positive");
        }

        this.priority = priority;
        this.itemInputs = List.copyOf(itemInputs);
        this.fluidInput = fluidInput;
        this.itemResults = List.copyOf(itemResults);
        this.fluidResult = fluidResult;
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
        return fluidInput.create();
    }

    public List<ItemStack> itemResults() {
        return itemResults.stream().map(ItemStackTemplate::create).toList();
    }

    public FluidStack fluidResult() {
        return fluidResult.create();
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
    public boolean matches(OverloadProcessingRecipeInput input, Level level) {
        return planMatch(input, 1).isPresent() && hasRequiredFluid(input.inputFluid(), 1);
    }

    public Optional<OverloadProcessingRecipeMatch> planMatch(OverloadProcessingRecipeInput input, int operations) {
        if (operations <= 0 || input == null) {
            return Optional.empty();
        }

        List<OverloadProcessingRecipeInput.SlotStack> slotStacks = input.slotStacks();
        if (itemInputs.isEmpty()) {
            if (!slotStacks.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new OverloadProcessingRecipeMatch(new int[OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT]));
        }

        if (slotStacks.isEmpty() || slotStacks.size() > OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT) {
            return Optional.empty();
        }

        int[] slotFlexibility = new int[slotStacks.size()];
        List<List<Integer>> rawMatches = new ArrayList<>(itemInputs.size());

        for (OverloadProcessingIngredient requirement : itemInputs) {
            List<Integer> matchingSlots = new ArrayList<>();
            long availableCount = 0L;
            long scaledRequirement = (long) requirement.count() * operations;

            for (int slotIndex = 0; slotIndex < slotStacks.size(); slotIndex++) {
                var slotStack = slotStacks.get(slotIndex);
                if (!requirement.ingredient().test(slotStack.stack())) {
                    continue;
                }

                matchingSlots.add(slotIndex);
                availableCount += slotStack.stack().getCount();
                slotFlexibility[slotIndex]++;
            }

            if (availableCount < scaledRequirement) {
                return Optional.empty();
            }

            rawMatches.add(matchingSlots);
        }

        List<RequirementState> requirements = new ArrayList<>(itemInputs.size());
        for (int requirementIndex = 0; requirementIndex < itemInputs.size(); requirementIndex++) {
            OverloadProcessingIngredient requirement = itemInputs.get(requirementIndex);
            List<Integer> matchingSlots = rawMatches.get(requirementIndex);
            // When multiple slots can satisfy the same ingredient, prefer the
            // machine's natural top-to-bottom slot order so repeated crafts
            // drain upper rows before lower ones.
            matchingSlots.sort(Comparator
                    .comparingInt((Integer slotIndex) -> slotStacks.get(slotIndex).slot()));
            requirements.add(new RequirementState(
                    multiplyExactToInt(requirement.count(), operations),
                    matchingSlots.stream().mapToInt(Integer::intValue).toArray()));
        }

        requirements.sort(Comparator
                .comparingInt(RequirementState::matchingSlotCount)
                .thenComparing(Comparator.comparingInt(RequirementState::count).reversed()));

        int[] remainingCounts = slotStacks.stream().mapToInt(slotStack -> slotStack.stack().getCount()).toArray();
        int[] slotConsumptions = new int[OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT];
        if (!allocateRequirement(0, requirements, slotStacks, remainingCounts, slotConsumptions)) {
            return Optional.empty();
        }

        return Optional.of(new OverloadProcessingRecipeMatch(slotConsumptions));
    }

    public boolean hasRequiredFluid(FluidStack availableFluid, int operations) {
        if (operations <= 0) {
            return false;
        }
        if (fluidInput.isEmpty()) {
            return true;
        }
        FluidStack requiredFluid = fluidInput.create();
        return !availableFluid.isEmpty()
                && FluidStack.isSameFluidSameComponents(requiredFluid, availableFluid)
                && availableFluid.getAmount() >= multiplyExactToInt(requiredFluid.getAmount(), operations);
    }

    public List<ItemStack> getScaledItemResults(int operations) {
        return itemResults.stream()
                .map(ItemStackTemplate::create)
                .map(stack -> stack.copyWithCount(multiplyExactToInt(stack.getCount(), operations)))
                .toList();
    }

    public FluidStack getScaledFluidResult(int operations) {
        if (fluidResult.isEmpty()) {
            return FluidStack.EMPTY;
        }
        FluidStack result = fluidResult.create();
        return result.copyWithAmount(multiplyExactToInt(result.getAmount(), operations));
    }

    @Override
    public ItemStack assemble(OverloadProcessingRecipeInput input) {
        return itemResults.isEmpty() ? ItemStack.EMPTY : itemResults.getFirst().create();
    }

    public ItemStack getResultItem() {
        return itemResults.isEmpty() ? ItemStack.EMPTY : itemResults.getFirst().create();
    }

    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (var input : itemInputs) {
            ingredients.add(input.ingredient());
        }
        return ingredients;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<OverloadProcessingRecipe> getSerializer() {
        return ModRecipeTypes.OVERLOAD_PROCESSING_SERIALIZER.get();
    }

    @Override
    public RecipeType<OverloadProcessingRecipe> getType() {
        return ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    public boolean isIncomplete() {
        return totalEnergy < MIN_TOTAL_ENERGY
                || lightningCost <= 0
                || (itemInputs.isEmpty() && fluidInput.isEmpty())
                || (itemResults.isEmpty() && fluidResult.isEmpty())
                || itemInputs.stream().anyMatch(input -> input.ingredient().isEmpty());
    }

    private OverloadProcessingFluidStackTemplate rawFluidInputTemplate() {
        return fluidInput;
    }

    private List<ItemStackTemplate> rawItemResultTemplates() {
        return itemResults;
    }

    private OverloadProcessingFluidStackTemplate rawFluidResultTemplate() {
        return fluidResult;
    }

    private boolean allocateRequirement(
            int requirementIndex,
            List<RequirementState> requirements,
            List<OverloadProcessingRecipeInput.SlotStack> slotStacks,
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
            List<OverloadProcessingRecipeInput.SlotStack> slotStacks,
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

    private static int multiplyExactToInt(int value, int multiplier) {
        long result = (long) value * multiplier;
        if (result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("scaled stack size exceeds integer range");
        }
        return (int) result;
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

    public static final class Serializer {
        public static final MapCodec<OverloadProcessingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.INT.optionalFieldOf("priority", 0).forGetter(OverloadProcessingRecipe::priority),
                        INPUTS_CODEC.optionalFieldOf("inputs", List.of()).forGetter(OverloadProcessingRecipe::itemInputs),
                        OverloadProcessingFluidStackTemplate.CODEC
                                .optionalFieldOf("inputFluid", OverloadProcessingFluidStackTemplate.EMPTY)
                                .forGetter(OverloadProcessingRecipe::rawFluidInputTemplate),
                        OUTPUTS_CODEC.optionalFieldOf("results", List.of())
                                .forGetter(OverloadProcessingRecipe::rawItemResultTemplates),
                        OverloadProcessingFluidStackTemplate.CODEC
                                .optionalFieldOf("resultFluid", OverloadProcessingFluidStackTemplate.EMPTY)
                                .forGetter(OverloadProcessingRecipe::rawFluidResultTemplate),
                        POSITIVE_ENERGY_CODEC.fieldOf("totalEnergy").forGetter(OverloadProcessingRecipe::totalEnergy),
                        POSITIVE_LIGHTNING_COST_CODEC.optionalFieldOf("lightningCost", DEFAULT_LIGHTNING_COST)
                                .forGetter(OverloadProcessingRecipe::lightningCost),
                        LightningKey.Tier.CODEC.optionalFieldOf("lightningTier", DEFAULT_LIGHTNING_TIER)
                                .forGetter(OverloadProcessingRecipe::lightningTier))
                .apply(instance, OverloadProcessingRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, OverloadProcessingRecipe> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public OverloadProcessingRecipe decode(RegistryFriendlyByteBuf buffer) {
                        return new OverloadProcessingRecipe(
                                ByteBufCodecs.VAR_INT.decode(buffer),
                                INPUTS_STREAM_CODEC.decode(buffer),
                                OverloadProcessingFluidStackTemplate.STREAM_CODEC.decode(buffer),
                                OUTPUTS_STREAM_CODEC.decode(buffer),
                                OverloadProcessingFluidStackTemplate.STREAM_CODEC.decode(buffer),
                                ByteBufCodecs.VAR_LONG.decode(buffer),
                                ByteBufCodecs.VAR_INT.decode(buffer),
                                TIER_STREAM_CODEC.decode(buffer));
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, OverloadProcessingRecipe recipe) {
                        ByteBufCodecs.VAR_INT.encode(buffer, recipe.priority());
                        INPUTS_STREAM_CODEC.encode(buffer, recipe.itemInputs());
                        OverloadProcessingFluidStackTemplate.STREAM_CODEC.encode(buffer, recipe.rawFluidInputTemplate());
                        OUTPUTS_STREAM_CODEC.encode(buffer, recipe.rawItemResultTemplates());
                        OverloadProcessingFluidStackTemplate.STREAM_CODEC.encode(buffer, recipe.rawFluidResultTemplate());
                        ByteBufCodecs.VAR_LONG.encode(buffer, recipe.totalEnergy());
                        ByteBufCodecs.VAR_INT.encode(buffer, recipe.lightningCost());
                        TIER_STREAM_CODEC.encode(buffer, recipe.lightningTier());
                    }
                };
        public MapCodec<OverloadProcessingRecipe> codec() {
            return CODEC;
        }
        public StreamCodec<RegistryFriendlyByteBuf, OverloadProcessingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
