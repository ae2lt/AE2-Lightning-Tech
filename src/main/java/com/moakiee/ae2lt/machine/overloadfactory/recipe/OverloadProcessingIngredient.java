package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import com.google.gson.JsonObject;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;

public record OverloadProcessingIngredient(Ingredient ingredient, int count) {
    public OverloadProcessingIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }

    public static OverloadProcessingIngredient fromJson(JsonObject json) {
        if (!json.has("ingredient")) {
            throw new IllegalArgumentException("Missing required field 'ingredient'");
        }
        return new OverloadProcessingIngredient(
                Ingredient.fromJson(json.get("ingredient")),
                GsonHelper.getAsInt(json, "count"));
    }

    public static OverloadProcessingIngredient fromNetwork(FriendlyByteBuf buffer) {
        return new OverloadProcessingIngredient(Ingredient.fromNetwork(buffer), buffer.readInt());
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        ingredient.toNetwork(buffer);
        buffer.writeInt(count);
    }
}
