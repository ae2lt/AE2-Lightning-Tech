package com.moakiee.ae2lt.machine.lightningchamber.recipe;

import com.google.gson.JsonObject;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;

public record LightningSimulationIngredient(Ingredient ingredient, int count) {
    public LightningSimulationIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }

    public static LightningSimulationIngredient fromJson(JsonObject json) {
        if (!json.has("ingredient")) {
            throw new IllegalArgumentException("Missing required field 'ingredient'");
        }
        return new LightningSimulationIngredient(
                Ingredient.fromJson(json.get("ingredient")),
                GsonHelper.getAsInt(json, "count"));
    }

    public static LightningSimulationIngredient fromNetwork(FriendlyByteBuf buffer) {
        return new LightningSimulationIngredient(Ingredient.fromNetwork(buffer), buffer.readInt());
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        ingredient.toNetwork(buffer);
        buffer.writeInt(count);
    }
}
