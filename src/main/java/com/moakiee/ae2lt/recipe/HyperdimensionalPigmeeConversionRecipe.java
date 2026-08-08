package com.moakiee.ae2lt.recipe;

import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModRecipeTypes;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Spends the Hyperdimensional Pigmee's remaining conversion and returns it as an ordinary Pigmee.
 * Unlike the Creative Pigmee recipe, this recipe has no arbitrary-item duplication fallback.
 */
public final class HyperdimensionalPigmeeConversionRecipe extends CustomRecipe {
    public HyperdimensionalPigmeeConversionRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    // 1.20.1 crafts against a CraftingContainer; assemble() takes a RegistryAccess.
    @Override
    public boolean matches(CraftingContainer input, Level level) {
        return !findTarget(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registryAccess) {
        return PigmeeConversionLogic.createResult(findTarget(input));
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.HYPERDIMENSIONAL_PIGMEE_CONVERSION_SERIALIZER.get();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.getContainerSize(); slot++) {
            if (input.getItem(slot).is(ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO_ITEM.get())) {
                remaining.set(slot, new ItemStack(ModFumos.PIGMEE_FUMO_ITEM.get()));
                break;
            }
        }
        return remaining;
    }

    private static ItemStack findTarget(CraftingContainer input) {
        ItemStack target = ItemStack.EMPTY;
        boolean foundCatalyst = false;

        for (int slot = 0; slot < input.getContainerSize(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.is(ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO_ITEM.get())) {
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

        return foundCatalyst && PigmeeConversionLogic.canConvert(target)
                ? target
                : ItemStack.EMPTY;
    }
}
