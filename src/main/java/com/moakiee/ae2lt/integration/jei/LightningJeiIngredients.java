package com.moakiee.ae2lt.integration.jei;

import java.util.List;

import com.mojang.serialization.Codec;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.me.key.LightningKey;

import mezz.jei.api.ingredients.IIngredientType;

public final class LightningJeiIngredients {
    public static final IIngredientType<LightningKey> TYPE = new IIngredientType<>() {
        @Override
        public String getUid() {
            return AE2LightningTech.MODID + ":lightning";
        }

        @Override
        public Class<? extends LightningKey> getIngredientClass() {
            return LightningKey.class;
        }
    };

    public static final Codec<LightningKey> CODEC = LightningKey.MAP_CODEC.codec();

    /**
     * Register the type without adding synthetic "lightning entries" to JEI's main ingredient list.
     * The type is still available for recipe slots, bookmarks, and AE2 screen lookups.
     */
    public static final List<LightningKey> INGREDIENTS = List.of();
    public static final LightningJeiIngredientHelper HELPER = new LightningJeiIngredientHelper();
    public static final LightningJeiIngredientRenderer RENDERER = new LightningJeiIngredientRenderer();

    private LightningJeiIngredients() {
    }
}
