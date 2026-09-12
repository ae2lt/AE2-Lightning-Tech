package com.moakiee.ae2lt.recipe;

import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** A normal, visible shaped recipe whose Pigmee catalyst is returned with its components intact. */
public final class PigmeeBuildingRecipe extends ShapedRecipe {
    private PigmeeBuildingRecipe(ShapedRecipe recipe) {
        // Vanilla shaped recipes store a fixed result; getResultItem does not consult registries.
        super(recipe.getGroup(), recipe.category(), recipe.pattern, recipe.getResultItem(null),
                recipe.showNotification());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.PIGMEE_BUILDING_SERIALIZER.get();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        var remaining = super.getRemainingItems(input);
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.is(ModFumos.PIGMEE_FUMO_ITEM.get())) {
                remaining.set(slot, stack.copyWithCount(1));
            }
        }
        return remaining;
    }

    public static final class Serializer implements RecipeSerializer<PigmeeBuildingRecipe> {
        private static final MapCodec<PigmeeBuildingRecipe> CODEC =
                ShapedRecipe.Serializer.CODEC.xmap(PigmeeBuildingRecipe::new, recipe -> recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf, PigmeeBuildingRecipe> STREAM_CODEC =
                ShapedRecipe.Serializer.STREAM_CODEC.map(PigmeeBuildingRecipe::new, recipe -> recipe);

        @Override
        public MapCodec<PigmeeBuildingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PigmeeBuildingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
