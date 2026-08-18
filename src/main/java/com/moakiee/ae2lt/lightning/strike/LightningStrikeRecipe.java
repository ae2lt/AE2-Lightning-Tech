package com.moakiee.ae2lt.lightning.strike;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;

import com.moakiee.ae2lt.registry.ModRecipeTypes;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Data-driven multiblock lightning strike recipe.
 *
 * <p>A recipe describes a 3D arrangement of blocks around a "center" block. When
 * a lightning bolt hits the structure and (optionally) was naturally spawned, the
 * center block is transformed into {@link #centerOutput()} and every
 * requirement marked with {@code consume = true} is removed.</p>
 *
 * <p>Matching is performed by {@code NaturalLightningTransformationHandler}
 * via the world; the {@link Recipe} interface is only implemented so the recipe
 * participates in the vanilla datapack and JEI infrastructure.</p>
 */
public final class LightningStrikeRecipe implements Recipe<LightningStrikeRecipeInput> {
    private final ResourceLocation id;
    private final boolean requiresNaturalLightning;
    private final Block centerInput;
    private final Block centerOutput;
    private final List<StructureRequirement> requirements;

    public LightningStrikeRecipe(
            ResourceLocation id,
            boolean requiresNaturalLightning,
            Block centerInput,
            Block centerOutput,
            List<StructureRequirement> requirements) {
        this.id = Objects.requireNonNull(id, "id");
        this.requiresNaturalLightning = requiresNaturalLightning;
        this.centerInput = Objects.requireNonNull(centerInput, "centerInput");
        this.centerOutput = Objects.requireNonNull(centerOutput, "centerOutput");
        this.requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
    }

    public boolean requiresNaturalLightning() {
        return requiresNaturalLightning;
    }

    public Block centerInput() {
        return centerInput;
    }

    public Block centerOutput() {
        return centerOutput;
    }

    public List<StructureRequirement> requirements() {
        return requirements;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public boolean matches(LightningStrikeRecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(LightningStrikeRecipeInput input, RegistryAccess registries) {
        return new ItemStack(centerOutput);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return new ItemStack(centerOutput);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.LIGHTNING_STRIKE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.LIGHTNING_STRIKE_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static final class Serializer implements RecipeSerializer<LightningStrikeRecipe> {
        @Override
        public LightningStrikeRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            JsonArray requirementsJson = GsonHelper.getAsJsonArray(json, "requirements");
            List<StructureRequirement> requirements = new java.util.ArrayList<>(requirementsJson.size());
            for (var element : requirementsJson) {
                requirements.add(StructureRequirement.fromJson(GsonHelper.convertToJsonObject(element, "requirements[]")));
            }

            return new LightningStrikeRecipe(
                    recipeId,
                    GsonHelper.getAsBoolean(json, "requires_natural_lightning", false),
                    BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(GsonHelper.getAsString(json, "center_input")))
                            .orElseThrow(() -> new IllegalArgumentException("Unknown block id for center_input")),
                    BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(GsonHelper.getAsString(json, "center_output")))
                            .orElseThrow(() -> new IllegalArgumentException("Unknown block id for center_output")),
                    requirements);
        }

        @Override
        public LightningStrikeRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            boolean requiresNatural = buffer.readBoolean();
            Block centerInput = BuiltInRegistries.BLOCK.getOptional(buffer.readResourceLocation())
                    .orElseThrow(() -> new IllegalStateException("Received unknown center_input block id"));
            Block centerOutput = BuiltInRegistries.BLOCK.getOptional(buffer.readResourceLocation())
                    .orElseThrow(() -> new IllegalStateException("Received unknown center_output block id"));
            int requirementCount = buffer.readInt();
            List<StructureRequirement> requirements = new java.util.ArrayList<>(requirementCount);
            for (int i = 0; i < requirementCount; i++) {
                requirements.add(StructureRequirement.fromNetwork(buffer));
            }

            return new LightningStrikeRecipe(recipeId, requiresNatural, centerInput, centerOutput, requirements);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, LightningStrikeRecipe recipe) {
            buffer.writeBoolean(recipe.requiresNaturalLightning());
            buffer.writeResourceLocation(BuiltInRegistries.BLOCK.getKey(recipe.centerInput()));
            buffer.writeResourceLocation(BuiltInRegistries.BLOCK.getKey(recipe.centerOutput()));
            buffer.writeInt(recipe.requirements().size());
            for (StructureRequirement requirement : recipe.requirements()) {
                requirement.toNetwork(buffer);
            }
        }
    }
}
