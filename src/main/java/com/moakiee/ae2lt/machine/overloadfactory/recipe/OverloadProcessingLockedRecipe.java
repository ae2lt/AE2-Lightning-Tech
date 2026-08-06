package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.Arrays;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import com.moakiee.ae2lt.me.key.LightningKey;

public final class OverloadProcessingLockedRecipe {
    private static final String TAG_RECIPE_ID = "RecipeId";
    private static final String TAG_TOTAL_ENERGY = "TotalEnergy";
    private static final String TAG_TOTAL_LIGHTNING_COST = "TotalLightningCost";
    private static final String TAG_LIGHTNING_TIER = "LightningTier";
    private static final String TAG_PARALLEL = "Parallel";
    private static final String TAG_INPUTS = "InputConsumptions";

    private final ResourceLocation recipeId;
    private final long totalEnergy;
    private final long totalLightningCost;
    private final LightningKey.Tier lightningTier;
    private final int parallel;
    private final int[] inputConsumptions;

    public OverloadProcessingLockedRecipe(
            ResourceLocation recipeId,
            long totalEnergy,
            long totalLightningCost,
            LightningKey.Tier lightningTier,
            int parallel,
            int[] inputConsumptions) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.totalEnergy = totalEnergy;
        this.totalLightningCost = totalLightningCost;
        this.lightningTier = Objects.requireNonNull(lightningTier, "lightningTier");
        int maxParallel = OverloadProcessingFactoryInventory.getMaxParallel();
        if (parallel <= 0 || parallel > maxParallel) {
            throw new IllegalArgumentException("parallel must be in range 1.." + maxParallel);
        }
        if (totalEnergy <= 0L) {
            throw new IllegalArgumentException("totalEnergy must be positive");
        }
        if (totalLightningCost <= 0L) {
            throw new IllegalArgumentException("totalLightningCost must be positive");
        }
        if (inputConsumptions.length != OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT) {
            throw new IllegalArgumentException("inputConsumptions must have length 9");
        }
        this.parallel = parallel;
        this.inputConsumptions = Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public static OverloadProcessingLockedRecipe fromCandidate(OverloadProcessingRecipeCandidate candidate) {
        RecipeHolder<OverloadProcessingRecipe> holder = candidate.recipe();
        return new OverloadProcessingLockedRecipe(
                holder.id(),
                candidate.totalEnergy(),
                candidate.totalLightningCost(),
                holder.value().lightningTier(),
                candidate.parallel(),
                candidate.match().inputConsumptions());
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    public long totalEnergy() {
        return totalEnergy;
    }

    public long totalLightningCost() {
        return totalLightningCost;
    }

    public LightningKey.Tier lightningTier() {
        return lightningTier;
    }

    public int parallel() {
        return parallel;
    }

    public int inputConsumptionForSlot(int slot) {
        if (slot < OverloadProcessingFactoryInventory.SLOT_INPUT_0
                || slot > OverloadProcessingFactoryInventory.SLOT_INPUT_8) {
            throw new IllegalArgumentException("slot must be an input slot");
        }
        return inputConsumptions[slot];
    }

    public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_RECIPE_ID, recipeId.toString());
        tag.putLong(TAG_TOTAL_ENERGY, totalEnergy);
        tag.putLong(TAG_TOTAL_LIGHTNING_COST, totalLightningCost);
        tag.putString(TAG_LIGHTNING_TIER, lightningTier.getSerializedName());
        tag.putInt(TAG_PARALLEL, parallel);
        tag.put(TAG_INPUTS, new IntArrayTag(Arrays.copyOf(inputConsumptions, inputConsumptions.length)));
        return tag;
    }

    @Nullable
    public static OverloadProcessingLockedRecipe fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains(TAG_RECIPE_ID, Tag.TAG_STRING)) {
            return null;
        }

        long totalEnergy = tag.getLong(TAG_TOTAL_ENERGY);
        long totalLightningCost = tag.getLong(TAG_TOTAL_LIGHTNING_COST);
        int parallel = tag.getInt(TAG_PARALLEL);
        int[] inputConsumptions = tag.getIntArray(TAG_INPUTS);
        if (inputConsumptions.length != OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT) {
            return null;
        }
        if (totalEnergy <= 0L || totalLightningCost <= 0L || parallel <= 0) {
            return null;
        }

        LightningKey.Tier lightningTier = tag.contains(TAG_LIGHTNING_TIER, Tag.TAG_STRING)
                ? LightningKey.Tier.fromSerializedName(tag.getString(TAG_LIGHTNING_TIER))
                : OverloadProcessingRecipe.DEFAULT_LIGHTNING_TIER;
        return new OverloadProcessingLockedRecipe(
                ResourceLocation.parse(tag.getString(TAG_RECIPE_ID)),
                totalEnergy,
                totalLightningCost,
                lightningTier,
                parallel,
                inputConsumptions);
    }
}
