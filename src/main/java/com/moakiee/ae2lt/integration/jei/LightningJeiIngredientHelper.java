package com.moakiee.ae2lt.integration.jei;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.me.key.LightningKey;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class LightningJeiIngredientHelper implements IIngredientHelper<LightningKey> {
    @Override
    public IIngredientType<LightningKey> getIngredientType() {
        return LightningJeiIngredients.TYPE;
    }

    @Override
    public String getDisplayName(LightningKey ingredient) {
        return ingredient.getDisplayName().getString();
    }

    @Override
    public Object getUid(LightningKey ingredient, UidContext context) {
        return ingredient.getId();
    }

    @Override
    @SuppressWarnings("removal")
    public String getUniqueId(LightningKey ingredient, UidContext context) {
        return ingredient.getId().toString();
    }

    @Override
    public Object getGroupingUid(LightningKey ingredient) {
        return ingredient.getId();
    }

    @Override
    public long getAmount(LightningKey ingredient) {
        return 1;
    }

    @Override
    public ResourceLocation getResourceLocation(LightningKey ingredient) {
        return ingredient.getId();
    }

    @Override
    public ItemStack getCheatItemStack(LightningKey ingredient) {
        return ItemStack.EMPTY;
    }

    @Override
    public LightningKey copyIngredient(LightningKey ingredient) {
        return LightningKey.of(ingredient.tier());
    }

    @Override
    public LightningKey normalizeIngredient(LightningKey ingredient) {
        return LightningKey.of(ingredient.tier());
    }

    @Override
    public String getErrorInfo(@Nullable LightningKey ingredient) {
        return ingredient == null ? "null lightning key" : ingredient.toString();
    }
}
