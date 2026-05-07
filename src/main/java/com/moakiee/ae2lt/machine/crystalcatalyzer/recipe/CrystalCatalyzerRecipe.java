package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

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

import com.moakiee.ae2lt.registry.ModRecipeTypes;

/**
 * Crystal catalyzer recipe.
 *
 * <p>Fields:</p>
 * <ul>
 *     <li>{@code catalyst}: optional. When absent, the catalyst slot must be empty;
 *         when present, the slot content must match the ingredient and have at least
 *         {@code catalystCount} items (the stack is <em>not</em> consumed).</li>
 *     <li>{@code output}: base per-cycle item output (final count = {@code output.count} × matrix multiplier).</li>
 *     <li>{@code energyPerCycle}: total energy (AE) consumed per cycle.</li>
 * </ul>
 *
 * <p>Fluid cost is <strong>not</strong> part of the recipe anymore — the machine always
 * drains a fixed amount of water per cycle regardless of which recipe runs (see
 * {@code CrystalCatalyzerBlockEntity.FIXED_FLUID_PER_CYCLE}).</p>
 */
public final class CrystalCatalyzerRecipe implements Recipe<CrystalCatalyzerRecipeInput> {
    public static final int MIN_ENERGY_PER_CYCLE = 1;

    private static final Codec<Integer> POSITIVE_ENERGY_CODEC = Codec.INT.validate(energy -> {
        if (energy < MIN_ENERGY_PER_CYCLE) {
            return DataResult.error(() -> "energyPerCycle must be at least " + MIN_ENERGY_PER_CYCLE);
        }
        return DataResult.success(energy);
    });

    private static final Codec<Integer> NON_NEGATIVE_COUNT_CODEC = Codec.INT.validate(count -> {
        if (count < 0) {
            return DataResult.error(() -> "count must be non-negative");
        }
        return DataResult.success(count);
    });

    private final Optional<Ingredient> catalyst;
    private final int catalystCount;
    private final CrystalCatalyzerOutput output;
    private final int energyPerCycle;
    private final Mode mode;

    public CrystalCatalyzerRecipe(
            Optional<Ingredient> catalyst,
            int catalystCount,
            ItemStack output,
            int energyPerCycle) {
        this(catalyst, catalystCount, CrystalCatalyzerOutput.ofItem(output), energyPerCycle, Mode.CRYSTAL);
    }

    public CrystalCatalyzerRecipe(
            Optional<Ingredient> catalyst,
            int catalystCount,
            CrystalCatalyzerOutput output,
            int energyPerCycle,
            Mode mode) {
        this.catalyst = Objects.requireNonNull(catalyst, "catalyst");
        this.catalystCount = catalystCount;
        this.output = Objects.requireNonNull(output, "output");
        this.energyPerCycle = energyPerCycle;
        this.mode = Objects.requireNonNull(mode, "mode");
        if (catalyst.isPresent() && catalystCount <= 0) {
            throw new IllegalArgumentException("catalystCount must be positive when catalyst is present");
        }
        if (energyPerCycle < MIN_ENERGY_PER_CYCLE) {
            throw new IllegalArgumentException("energyPerCycle must be at least " + MIN_ENERGY_PER_CYCLE);
        }
    }

    public Optional<Ingredient> catalyst() {
        return catalyst;
    }

    public int catalystCount() {
        return catalystCount;
    }

    public ItemStack getOutputTemplate() {
        return output.resolve();
    }

    public CrystalCatalyzerOutput outputSpec() {
        return output;
    }

    public int energyPerCycle() {
        return energyPerCycle;
    }

    public Mode mode() {
        return mode;
    }

    public boolean catalystMatches(ItemStack stack) {
        if (catalyst.isEmpty()) {
            return stack.isEmpty();
        }
        if (stack.isEmpty() || !catalyst.get().test(stack)) {
            return false;
        }
        return stack.getCount() >= catalystCount;
    }

    @Override
    public boolean matches(CrystalCatalyzerRecipeInput input, Level level) {
        return catalystMatches(input.catalyst());
    }

    @Override
    public ItemStack assemble(CrystalCatalyzerRecipeInput input, HolderLookup.Provider registries) {
        return output.resolve();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return output.resolve();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        catalyst.ifPresent(list::add);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.CRYSTAL_CATALYZER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get();
    }

    @Override
    public boolean isIncomplete() {
        return output.resolve().isEmpty()
                || energyPerCycle < MIN_ENERGY_PER_CYCLE
                || (catalyst.isPresent() && catalystCount <= 0);
    }

    public static final class Serializer implements RecipeSerializer<CrystalCatalyzerRecipe> {
        private static final MapCodec<CrystalCatalyzerRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY.optionalFieldOf("catalyst").forGetter(CrystalCatalyzerRecipe::catalyst),
                        NON_NEGATIVE_COUNT_CODEC.optionalFieldOf("catalystCount", 0).forGetter(CrystalCatalyzerRecipe::catalystCount),
                        CrystalCatalyzerOutput.CODEC.fieldOf("output").forGetter(CrystalCatalyzerRecipe::outputSpec),
                        POSITIVE_ENERGY_CODEC.fieldOf("energyPerCycle").forGetter(CrystalCatalyzerRecipe::energyPerCycle),
                        Mode.CODEC.optionalFieldOf("mode", Mode.CRYSTAL).forGetter(CrystalCatalyzerRecipe::mode))
                .apply(instance, CrystalCatalyzerRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> OPTIONAL_INGREDIENT_STREAM_CODEC =
                ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC);

        private static final StreamCodec<RegistryFriendlyByteBuf, CrystalCatalyzerRecipe> STREAM_CODEC =
                StreamCodec.of(Serializer::encode, Serializer::decode);

        private static void encode(RegistryFriendlyByteBuf buf, CrystalCatalyzerRecipe recipe) {
            OPTIONAL_INGREDIENT_STREAM_CODEC.encode(buf, recipe.catalyst);
            ByteBufCodecs.VAR_INT.encode(buf, recipe.catalystCount);
            CrystalCatalyzerOutput.STREAM_CODEC.encode(buf, recipe.output);
            ByteBufCodecs.VAR_INT.encode(buf, recipe.energyPerCycle);
            ByteBufCodecs.VAR_INT.encode(buf, recipe.mode.ordinal());
        }

        private static CrystalCatalyzerRecipe decode(RegistryFriendlyByteBuf buf) {
            Optional<Ingredient> catalyst = OPTIONAL_INGREDIENT_STREAM_CODEC.decode(buf);
            int catalystCount = ByteBufCodecs.VAR_INT.decode(buf);
            CrystalCatalyzerOutput output = CrystalCatalyzerOutput.STREAM_CODEC.decode(buf);
            int energyPerCycle = ByteBufCodecs.VAR_INT.decode(buf);
            int modeOrdinal = ByteBufCodecs.VAR_INT.decode(buf);
            Mode mode = modeOrdinal >= 0 && modeOrdinal < Mode.values().length
                    ? Mode.values()[modeOrdinal]
                    : Mode.CRYSTAL;
            return new CrystalCatalyzerRecipe(catalyst, catalystCount, output, energyPerCycle, mode);
        }

        @Override
        public MapCodec<CrystalCatalyzerRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CrystalCatalyzerRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
