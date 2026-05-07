package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

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
        RecipeHolder<CrystalCatalyzerRecipe> holder = candidate.recipe();
        CrystalCatalyzerRecipe recipe = holder.value();
        return new CrystalCatalyzerLockedRecipe(
                holder.id(),
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

    public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_RECIPE_ID, recipeId.toString());
        tag.put(TAG_OUTPUT, output.save(registries, new CompoundTag()));
        tag.putInt(TAG_ENERGY, energyPerCycle);
        tag.putInt(TAG_OUTPUT_MULTIPLIER, outputMultiplier);
        return tag;
    }

    @Nullable
    public static CrystalCatalyzerLockedRecipe fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        return fromTag(tag, registries, 1);
    }

    @Nullable
    public static CrystalCatalyzerLockedRecipe fromTag(
            CompoundTag tag,
            HolderLookup.Provider registries,
            int defaultOutputMultiplier) {
        if (!tag.contains(TAG_RECIPE_ID) || !tag.contains(TAG_OUTPUT, Tag.TAG_COMPOUND)) {
            return null;
        }

        ItemStack output = ItemStack.parseOptional(registries, tag.getCompound(TAG_OUTPUT));
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
                ResourceLocation.parse(tag.getString(TAG_RECIPE_ID)),
                output,
                energy,
                outputMultiplier);
    }
}
