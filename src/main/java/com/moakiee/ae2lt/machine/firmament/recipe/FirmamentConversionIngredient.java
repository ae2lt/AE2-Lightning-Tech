package com.moakiee.ae2lt.machine.firmament.recipe;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.crafting.Ingredient;

public record FirmamentConversionIngredient(Ingredient ingredient, int count) {
    private static final Codec<Integer> POSITIVE_COUNT_CODEC = Codec.intRange(1, Integer.MAX_VALUE);

    // 1.20.1 Ingredient 无 CODEC 字段:通过 JSON 编解码(与 datagen/手工配方 JSON 一致)。
    private static final Codec<Ingredient> INGREDIENT_CODEC =
            ExtraCodecs.JSON.xmap(Ingredient::fromJson, Ingredient::toJson);

    public static final MapCodec<FirmamentConversionIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    INGREDIENT_CODEC.fieldOf("ingredient").forGetter(FirmamentConversionIngredient::ingredient),
                    POSITIVE_COUNT_CODEC.fieldOf("count").forGetter(FirmamentConversionIngredient::count))
            .apply(instance, FirmamentConversionIngredient::new));

    public FirmamentConversionIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
