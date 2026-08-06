package com.moakiee.ae2lt.machine.firmament.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import com.moakiee.ae2lt.machine.firmament.FirmamentConversionInventory;

public final class FirmamentConversionLockedRecipe {
    private static final String TAG_RECIPE_ID = "RecipeId";
    private static final String TAG_RESULT = "Result";
    private static final String TAG_RESULTS = "Results";
    private static final String TAG_PROCESS_TIME = "ProcessTime";
    private static final String TAG_INPUTS = "InputConsumptions";

    private final ResourceLocation recipeId;
    private final List<ItemStack> results;
    private final int processTime;
    private final int[] inputConsumptions;

    public FirmamentConversionLockedRecipe(
            ResourceLocation recipeId,
            List<ItemStack> results,
            int processTime,
            int[] inputConsumptions) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(results, "results");
        this.processTime = processTime;
        if (results.isEmpty() || results.size() > FirmamentConversionInventory.OUTPUT_SLOT_COUNT) {
            throw new IllegalArgumentException("results must contain 1 to 4 entries");
        }
        if (results.stream().anyMatch(ItemStack::isEmpty)) {
            throw new IllegalArgumentException("results cannot contain empty stacks");
        }
        if (processTime <= 0) {
            throw new IllegalArgumentException("processTime must be positive");
        }
        if (inputConsumptions.length != 3) {
            throw new IllegalArgumentException("inputConsumptions must have length 3");
        }
        this.results = results.stream().map(ItemStack::copy).toList();
        this.inputConsumptions = Arrays.copyOf(inputConsumptions, inputConsumptions.length);
    }

    public FirmamentConversionLockedRecipe(
            ResourceLocation recipeId,
            ItemStack result,
            int processTime,
            int[] inputConsumptions) {
        this(recipeId, List.of(result), processTime, inputConsumptions);
    }

    public static FirmamentConversionLockedRecipe fromCandidate(FirmamentConversionRecipeCandidate candidate) {
        RecipeHolder<FirmamentConversionRecipe> holder = candidate.recipe();
        return new FirmamentConversionLockedRecipe(
                holder.id(),
                holder.value().getResultStacks(),
                holder.value().processTime(),
                candidate.match().inputConsumptions());
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    public ItemStack result() {
        return results.getFirst().copy();
    }

    public List<ItemStack> results() {
        return results.stream().map(ItemStack::copy).toList();
    }

    public int processTime() {
        return processTime;
    }

    public int inputConsumptionForSlot(int slot) {
        if (slot < FirmamentConversionInventory.SLOT_INPUT_0
                || slot > FirmamentConversionInventory.SLOT_INPUT_2) {
            throw new IllegalArgumentException("slot must be one of the three input slots");
        }
        return inputConsumptions[slot];
    }

    public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_RECIPE_ID, recipeId.toString());
        ListTag resultTags = new ListTag();
        for (ItemStack result : results) {
            resultTags.add(result.save(registries, new CompoundTag()));
        }
        tag.put(TAG_RESULTS, resultTags);
        tag.putInt(TAG_PROCESS_TIME, processTime);
        tag.put(TAG_INPUTS, new IntArrayTag(Arrays.copyOf(inputConsumptions, inputConsumptions.length)));
        return tag;
    }

    @Nullable
    public static FirmamentConversionLockedRecipe fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains(TAG_RECIPE_ID)) {
            return null;
        }

        List<ItemStack> results = readResults(tag, registries);
        if (results.isEmpty()) {
            return null;
        }

        int processTime = tag.getInt(TAG_PROCESS_TIME);
        int[] inputConsumptions = tag.getIntArray(TAG_INPUTS);
        if (processTime <= 0 || inputConsumptions.length != 3) {
            return null;
        }

        return new FirmamentConversionLockedRecipe(
                ResourceLocation.parse(tag.getString(TAG_RECIPE_ID)),
                results,
                processTime,
                inputConsumptions);
    }

    private static List<ItemStack> readResults(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains(TAG_RESULTS, Tag.TAG_LIST)) {
            ListTag resultTags = tag.getList(TAG_RESULTS, Tag.TAG_COMPOUND);
            if (resultTags.isEmpty() || resultTags.size() > FirmamentConversionInventory.OUTPUT_SLOT_COUNT) {
                return List.of();
            }

            List<ItemStack> results = new ArrayList<>(resultTags.size());
            for (int index = 0; index < resultTags.size(); index++) {
                ItemStack result = ItemStack.parseOptional(registries, resultTags.getCompound(index));
                if (result.isEmpty()) {
                    return List.of();
                }
                results.add(result);
            }
            return List.copyOf(results);
        }

        if (tag.contains(TAG_RESULT, Tag.TAG_COMPOUND)) {
            ItemStack result = ItemStack.parseOptional(registries, tag.getCompound(TAG_RESULT));
            return result.isEmpty() ? List.of() : List.of(result);
        }

        return List.of();
    }
}
