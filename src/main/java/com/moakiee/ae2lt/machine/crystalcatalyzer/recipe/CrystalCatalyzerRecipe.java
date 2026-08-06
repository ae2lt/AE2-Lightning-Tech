package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import com.google.gson.JsonObject;
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
    public static final int DEFAULT_LIGHTNING_COST = 1;
    public static final LightningKey.Tier DEFAULT_LIGHTNING_TIER = LightningKey.Tier.HIGH_VOLTAGE;

    private final ResourceLocation id;
    private final Optional<Ingredient> catalyst;
    private final int catalystCount;
    private final CrystalCatalyzerOutput output;
    private final int energyPerCycle;
    private final int lightningCost;
    private final LightningKey.Tier lightningTier;
    private final Mode mode;

    public CrystalCatalyzerRecipe(
            ResourceLocation id,
            Optional<Ingredient> catalyst,
            int catalystCount,
            ItemStack output,
            int energyPerCycle) {
        this(id, catalyst, catalystCount, CrystalCatalyzerOutput.ofItem(output), energyPerCycle,
                DEFAULT_LIGHTNING_COST, DEFAULT_LIGHTNING_TIER, Mode.CRYSTAL);
    }

    public CrystalCatalyzerRecipe(
            ResourceLocation id,
            Optional<Ingredient> catalyst,
            int catalystCount,
            CrystalCatalyzerOutput output,
            int energyPerCycle,
            int lightningCost,
            LightningKey.Tier lightningTier,
            Mode mode) {
        this.id = Objects.requireNonNull(id, "id");
        this.catalyst = Objects.requireNonNull(catalyst, "catalyst");
        this.catalystCount = catalystCount;
        this.output = Objects.requireNonNull(output, "output");
        this.energyPerCycle = energyPerCycle;
        this.lightningCost = lightningCost;
        this.lightningTier = Objects.requireNonNull(lightningTier, "lightningTier");
        this.mode = Objects.requireNonNull(mode, "mode");
        if (catalyst.isPresent() && catalystCount <= 0) {
            throw new IllegalArgumentException("catalystCount must be positive when catalyst is present");
        }
        if (energyPerCycle < MIN_ENERGY_PER_CYCLE) {
            throw new IllegalArgumentException("energyPerCycle must be at least " + MIN_ENERGY_PER_CYCLE);
        }
        if (lightningCost < 1) {
            throw new IllegalArgumentException("lightningCost must be at least 1");
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

    public int lightningCost() {
        return lightningCost;
    }

    public LightningKey.Tier lightningTier() {
        return lightningTier;
    }

    public Mode mode() {
        return mode;
    }

    @Override
    public ResourceLocation getId() {
        return id;
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
    public ItemStack assemble(CrystalCatalyzerRecipeInput input, RegistryAccess registries) {
        return output.resolve();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
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
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean isIncomplete() {
        return output.resolve().isEmpty()
                || energyPerCycle < MIN_ENERGY_PER_CYCLE
                || lightningCost < 1
                || (catalyst.isPresent() && catalystCount <= 0);
    }

    public static final class Serializer implements RecipeSerializer<CrystalCatalyzerRecipe> {
        @Override
        public CrystalCatalyzerRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            Optional<Ingredient> catalyst = json.has("catalyst")
                    ? Optional.of(Ingredient.fromJson(json.get("catalyst")))
                    : Optional.empty();
            int catalystCount = GsonHelper.getAsInt(json, "catalystCount", 0);
            int energyPerCycle = GsonHelper.getAsInt(json, "energyPerCycle");
            int lightningCost = GsonHelper.getAsInt(json, "lightningCost", DEFAULT_LIGHTNING_COST);
            LightningKey.Tier lightningTier = RecipeSerializationHelper.enumFromJson(
                    json,
                    "lightningTier",
                    DEFAULT_LIGHTNING_TIER,
                    LightningKey.Tier.values());
            Mode mode = RecipeSerializationHelper.enumFromJson(
                    json,
                    "mode",
                    Mode.CRYSTAL,
                    Mode.values());

            return new CrystalCatalyzerRecipe(
                    recipeId,
                    catalyst,
                    catalystCount,
                    CrystalCatalyzerOutput.fromJson(GsonHelper.getAsJsonObject(json, "output")),
                    energyPerCycle,
                    lightningCost,
                    lightningTier,
                    mode);
        }

        @Override
        public CrystalCatalyzerRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            Optional<Ingredient> catalyst = buffer.readBoolean()
                    ? Optional.of(Ingredient.fromNetwork(buffer))
                    : Optional.empty();
            return new CrystalCatalyzerRecipe(
                    recipeId,
                    catalyst,
                    buffer.readInt(),
                    CrystalCatalyzerOutput.decode(buffer),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readEnum(LightningKey.Tier.class),
                    buffer.readEnum(Mode.class));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, CrystalCatalyzerRecipe recipe) {
            buffer.writeBoolean(recipe.catalyst().isPresent());
            recipe.catalyst().ifPresent(ingredient -> ingredient.toNetwork(buffer));
            buffer.writeInt(recipe.catalystCount());
            CrystalCatalyzerOutput.encode(buffer, recipe.outputSpec());
            buffer.writeInt(recipe.energyPerCycle());
            buffer.writeInt(recipe.lightningCost());
            buffer.writeEnum(recipe.lightningTier());
            buffer.writeEnum(recipe.mode());
        }
    }
}
