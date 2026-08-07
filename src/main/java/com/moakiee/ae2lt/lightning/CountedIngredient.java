package com.moakiee.ae2lt.lightning;

import com.google.gson.JsonObject;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;

public record CountedIngredient(Ingredient ingredient, int count) {
    public CountedIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }

    public static CountedIngredient fromJson(JsonObject json) {
        if (!json.has("ingredient")) {
            throw new IllegalArgumentException("Missing required field 'ingredient'");
        }
        return new CountedIngredient(
                Ingredient.fromJson(json.get("ingredient")),
                GsonHelper.getAsInt(json, "count"));
    }

    public static CountedIngredient fromNetwork(FriendlyByteBuf buffer) {
        return new CountedIngredient(Ingredient.fromNetwork(buffer), buffer.readInt());
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        ingredient.toNetwork(buffer);
        buffer.writeInt(count);
    }
}
