package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class CrystalCatalyzerLockedRecipe {
    private static final String TAG_RECIPE_ID = "RecipeId";
    private static final String TAG_OUTPUT = "Output";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_OUTPUT_MULTIPLIER = "OutputMultiplier";

    private final ResourceLocation recipeId;
    private final ItemStack output;
    private final int energyPerCycle;
    private final int outputMultiplier;

    public CrystalCatalyzerLockedRecipe(
            ResourceLocation recipeId,
            ItemStack output,
            int energyPerCycle,
            int outputMultiplier) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.output = Objects.requireNonNull(output, "output").copy();
        this.energyPerCycle = energyPerCycle;
        this.outputMultiplier = outputMultiplier;
        if (output.isEmpty()) {
            throw new IllegalArgumentException("output cannot be empty");
        }
        if (energyPerCycle <= 0) {
            throw new IllegalArgumentException("energyPerCycle must be positive");
        }
        if (outputMultiplier <= 0) {
            throw new IllegalArgumentException("outputMultiplier must be positive");
        }
    }

    public static CrystalCatalyzerLockedRecipe fromCandidate(
            CrystalCatalyzerRecipeCandidate candidate,
            int outputMultiplier) {
        CrystalCatalyzerRecipe recipe = candidate.recipe();
        return new CrystalCatalyzerLockedRecipe(
                recipe.getId(),
                recipe.getOutputTemplate(),
                recipe.energyPerCycle(),
                outputMultiplier);
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    public ItemStack output() {
        return output.copy();
    }

    public int energyPerCycle() {
        return energyPerCycle;
    }

    public int outputMultiplier() {
        return outputMultiplier;
    }

    public long totalEnergy() {
        return energyPerCycle;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_RECIPE_ID, recipeId.toString());
        tag.put(TAG_OUTPUT, output.save(new CompoundTag()));
        tag.putInt(TAG_ENERGY, energyPerCycle);
        tag.putInt(TAG_OUTPUT_MULTIPLIER, outputMultiplier);
        return tag;
    }

    @Nullable
    public static CrystalCatalyzerLockedRecipe fromTag(CompoundTag tag) {
        return fromTag(tag, 1);
    }

    @Nullable
    public static CrystalCatalyzerLockedRecipe fromTag(
            CompoundTag tag,
            int defaultOutputMultiplier) {
        if (!tag.contains(TAG_RECIPE_ID) || !tag.contains(TAG_OUTPUT, Tag.TAG_COMPOUND)) {
            return null;
        }

        ItemStack output = ItemStack.of(tag.getCompound(TAG_OUTPUT));
        if (output.isEmpty()) {
            return null;
        }

        int energy = tag.getInt(TAG_ENERGY);
        if (energy <= 0) {
            return null;
        }

        int outputMultiplier = tag.contains(TAG_OUTPUT_MULTIPLIER, Tag.TAG_INT)
                ? tag.getInt(TAG_OUTPUT_MULTIPLIER)
                : defaultOutputMultiplier;
        if (outputMultiplier <= 0) {
            return null;
        }

        return new CrystalCatalyzerLockedRecipe(
                new ResourceLocation(tag.getString(TAG_RECIPE_ID)),
                output,
                energy,
                outputMultiplier);
    }
}
