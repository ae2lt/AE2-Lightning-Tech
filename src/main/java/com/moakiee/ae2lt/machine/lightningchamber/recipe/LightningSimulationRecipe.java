package com.moakiee.ae2lt.machine.lightningchamber.recipe;

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

import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModRecipeTypes;

public final class LightningSimulationRecipe implements Recipe<LightningSimulationRecipeInput> {
    public static final long MIN_TOTAL_ENERGY = 5L;
    public static final int DEFAULT_LIGHTNING_COST = 4;
    public static final LightningKey.Tier DEFAULT_LIGHTNING_TIER = LightningKey.Tier.HIGH_VOLTAGE;

    private static final Codec<List<LightningSimulationIngredient>> INPUTS_CODEC =
            LightningSimulationIngredient.CODEC.codec()
                    .listOf()
                    .validate(inputs -> {
                        if (inputs.isEmpty()) {
                            return DataResult.error(() -> "lightning simulation recipe inputs cannot be empty");
                        }
                        if (inputs.size() > 3) {
                            return DataResult.error(() -> "lightning simulation recipe supports at most 3 inputs");
                        }
                        return DataResult.success(List.copyOf(inputs));
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
    private static final StreamCodec<RegistryFriendlyByteBuf, List<LightningSimulationIngredient>> INPUTS_STREAM_CODEC =
            LightningSimulationIngredient.STREAM_CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<RegistryFriendlyByteBuf, LightningKey.Tier> TIER_STREAM_CODEC =
            StreamCodec.of(
                    (buffer, tier) -> buffer.writeEnum(tier),
                    buffer -> buffer.readEnum(LightningKey.Tier.class));

    private final int priority;
    private final List<LightningSimulationIngredient> inputs;
    private final ItemStack result;
    private final long totalEnergy;
    private final int lightningCost;
    private final LightningKey.Tier lightningTier;
    private final int totalInputCount;

    public LightningSimulationRecipe(
            int priority,
            List<LightningSimulationIngredient> inputs,
            ItemStack result,
            long totalEnergy,
            int lightningCost,
            LightningKey.Tier lightningTier) {
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
    public ItemStack assemble(LightningSimulationRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
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
                || inputs.stream().anyMatch(input -> input.ingredient().hasNoItems());
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
        private static final MapCodec<LightningSimulationRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.INT.optionalFieldOf("priority", 0).forGetter(LightningSimulationRecipe::priority),
                        INPUTS_CODEC.fieldOf("inputs").forGetter(LightningSimulationRecipe::inputs),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(LightningSimulationRecipe::rawResult),
                        POSITIVE_ENERGY_CODEC.fieldOf("totalEnergy").forGetter(LightningSimulationRecipe::totalEnergy),
                        POSITIVE_LIGHTNING_COST_CODEC.optionalFieldOf("lightningCost", DEFAULT_LIGHTNING_COST)
                                .forGetter(LightningSimulationRecipe::lightningCost),
                        LightningKey.Tier.CODEC.optionalFieldOf("lightningTier", DEFAULT_LIGHTNING_TIER)
                                .forGetter(LightningSimulationRecipe::lightningTier))
                .apply(instance, LightningSimulationRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, LightningSimulationRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT,
                        LightningSimulationRecipe::priority,
                        INPUTS_STREAM_CODEC,
                        LightningSimulationRecipe::inputs,
                        ItemStack.STREAM_CODEC,
                        LightningSimulationRecipe::rawResult,
                        ByteBufCodecs.VAR_LONG,
                        LightningSimulationRecipe::totalEnergy,
                        ByteBufCodecs.VAR_INT,
                        LightningSimulationRecipe::lightningCost,
                        TIER_STREAM_CODEC,
                        LightningSimulationRecipe::lightningTier,
                        LightningSimulationRecipe::new);

        @Override
        public MapCodec<LightningSimulationRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, LightningSimulationRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
