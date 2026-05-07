package com.moakiee.ae2lt.integration.jei.compat.ae2jeiintegration;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.integration.jei.LightningJeiIngredients;
import com.moakiee.ae2lt.me.key.LightningKey;

import appeng.api.stacks.GenericStack;
import mezz.jei.api.ingredients.IIngredientType;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters;

public final class AE2JeiIntegrationCompat {
    private static boolean registered;

    private AE2JeiIntegrationCompat() {
    }

    public static synchronized void registerConverter() {
        if (registered) {
            return;
        }

        IngredientConverters.register(LightningKeyIngredientConverter.INSTANCE);
        registered = true;
    }

    private static final class LightningKeyIngredientConverter implements IngredientConverter<LightningKey> {
        private static final LightningKeyIngredientConverter INSTANCE = new LightningKeyIngredientConverter();

        @Override
        public IIngredientType<LightningKey> getIngredientType() {
            return LightningJeiIngredients.TYPE;
        }

        @Override
        public @Nullable LightningKey getIngredientFromStack(GenericStack stack) {
            if (stack.what() instanceof LightningKey lightningKey) {
                return LightningKey.of(lightningKey.tier());
            }
            return null;
        }

        @Override
        public @Nullable GenericStack getStackFromIngredient(LightningKey ingredient) {
            if (ingredient == null) {
                return null;
            }
            return new GenericStack(LightningKey.of(ingredient.tier()), 1);
        }
    }
}
