package com.moakiee.ae2lt.recipe;

import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModRecipeTypes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * One Creative Pigmee transforms one known progression item, or copies any other item into a stack
 * of 64 while preserving all components. The Pigmee is returned as a catalyst and cannot copy
 * another Creative Pigmee.
 */
public final class CreativePigmeeDuplicationRecipe extends CustomRecipe {
    private static final int OUTPUT_COUNT = 64;

    public CreativePigmeeDuplicationRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !findTarget(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack target = findTarget(input);
        if (target.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack conversion = PigmeeConversionLogic.createResult(target);
        if (!conversion.isEmpty()) {
            return conversion;
        }

        return target.copyWithCount(OUTPUT_COUNT);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        var remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.is(ModFumos.CREATIVE_PIGMEE_FUMO_ITEM.get())) {
                remaining.set(slot, stack.copyWithCount(1));
                break;
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.CREATIVE_PIGMEE_DUPLICATION_SERIALIZER.get();
    }

    private static ItemStack findTarget(CraftingInput input) {
        ItemStack target = ItemStack.EMPTY;
        boolean foundCatalyst = false;

        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.is(ModFumos.CREATIVE_PIGMEE_FUMO_ITEM.get())) {
                if (foundCatalyst) {
                    return ItemStack.EMPTY;
                }
                foundCatalyst = true;
            } else if (target.isEmpty()) {
                target = stack;
            } else {
                return ItemStack.EMPTY;
            }
        }

        return foundCatalyst ? target : ItemStack.EMPTY;
    }
}
