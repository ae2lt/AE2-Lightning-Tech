package com.moakiee.ae2lt.machine.lightningassembly.recipe;

import java.util.Arrays;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import com.moakiee.ae2lt.machine.lightningassembly.LightningAssemblyChamberInventory;
import com.moakiee.ae2lt.me.key.LightningKey;

public final class LightningAssemblyLockedRecipe {
    private static final String TAG_RECIPE_ID = "RecipeId";
    private static final String TAG_RESULT = "Result";
    private static final String TAG_TOTAL_ENERGY = "TotalEnergy";
    private static final String TAG_LIGHTNING_COST = "LightningCost";
    private static final String TAG_LIGHTNING_TIER = "LightningTier";
    private static final String TAG_LEGACY_DUST_COST = "DustCost";
    private static final String TAG_INPUTS = "InputConsumptions";

    private final ResourceLocation recipeId;
    private final ItemStack result;
    private final long totalEnergy;
    private final int lightningCost;
    private final LightningKey.Tier lightningTier;
    private final int[] inputConsumptions;

    public LightningAssemblyLockedRecipe(
            ResourceLocation recipeId,
            ItemStack result,
            long totalEnergy,
            int lightningCost,
            LightningKey.Tier lightningTier,
            int[] inputConsumptions) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.result = Objects.requireNonNull(result, "result").copy();
        this.totalEnergy = totalEnergy;
        this.lightningCost = lightningCost;
        this.lightningTier = Objects.requireNonNull(lightningTier, "lightningTier");
        if (result.isEmpty()) {
            throw new IllegalArgumentException("result cannot be empty");
        }
        if (totalEnergy <= 0) {
            throw new IllegalArgumentException("totalEnergy must be positive");
        }
        if (lightningCost <= 0) {
            throw new IllegalArgumentException("lightningCost must be positive");
        }
        if (inputConsumptions.length != 9) {
            throw new IllegalArgumentException("inputConsumptions must have length 9");
        }
        this.inputConsumptions = Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public static LightningAssemblyLockedRecipe fromCandidate(LightningAssemblyRecipeCandidate candidate) {
        RecipeHolder<LightningAssemblyRecipe> holder = candidate.recipe();
        return new LightningAssemblyLockedRecipe(
                holder.id(),
                holder.value().getResultStack(),
                holder.value().totalEnergy(),
                holder.value().lightningCost(),
                holder.value().lightningTier(),
                candidate.match().inputConsumptions());
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    public ItemStack result() {
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

    public int[] inputConsumptions() {
        return Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public int inputConsumptionForSlot(int slot) {
        if (slot < LightningAssemblyChamberInventory.SLOT_INPUT_0
                || slot > LightningAssemblyChamberInventory.SLOT_INPUT_8) {
            throw new IllegalArgumentException("slot must be one of the nine input slots");
        }
        return inputConsumptions[slot];
    }

    public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_RECIPE_ID, recipeId.toString());
        tag.put(TAG_RESULT, result.save(registries, new CompoundTag()));
        tag.putLong(TAG_TOTAL_ENERGY, totalEnergy);
        tag.putInt(TAG_LIGHTNING_COST, lightningCost);
        tag.putString(TAG_LIGHTNING_TIER, lightningTier.getSerializedName());
        tag.put(TAG_INPUTS, new IntArrayTag(Arrays.copyOf(inputConsumptions, inputConsumptions.length)));
        return tag;
    }

    @Nullable
    public static LightningAssemblyLockedRecipe fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains(TAG_RECIPE_ID) || !tag.contains(TAG_RESULT, Tag.TAG_COMPOUND)) {
            return null;
        }

        ItemStack result = ItemStack.parseOptional(registries, tag.getCompound(TAG_RESULT));
        if (result.isEmpty()) {
            return null;
        }

        int[] inputConsumptions = tag.getIntArray(TAG_INPUTS);
        if (inputConsumptions.length != 9) {
            return null;
        }

        long totalEnergy = tag.getLong(TAG_TOTAL_ENERGY);
        int lightningCost = tag.contains(TAG_LIGHTNING_COST, Tag.TAG_ANY_NUMERIC)
                ? tag.getInt(TAG_LIGHTNING_COST)
                : (tag.getInt(TAG_LEGACY_DUST_COST) > 0 ? LightningAssemblyRecipe.DEFAULT_LIGHTNING_COST : 0);
        LightningKey.Tier lightningTier = tag.contains(TAG_LIGHTNING_TIER, Tag.TAG_STRING)
                ? LightningKey.Tier.fromSerializedName(tag.getString(TAG_LIGHTNING_TIER))
                : LightningAssemblyRecipe.DEFAULT_LIGHTNING_TIER;
        if (totalEnergy <= 0 || lightningCost <= 0) {
            return null;
        }

        return new LightningAssemblyLockedRecipe(
                ResourceLocation.parse(tag.getString(TAG_RECIPE_ID)),
                result,
                totalEnergy,
                lightningCost,
                lightningTier,
                inputConsumptions);
    }
}
