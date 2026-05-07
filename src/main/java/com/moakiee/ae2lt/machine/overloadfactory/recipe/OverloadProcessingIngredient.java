package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

public record OverloadProcessingIngredient(Ingredient ingredient, int count) {
    private static final Codec<Integer> POSITIVE_COUNT_CODEC = Codec.intRange(1, Integer.MAX_VALUE);

    public static final MapCodec<OverloadProcessingIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(OverloadProcessingIngredient::ingredient),
                    POSITIVE_COUNT_CODEC.fieldOf("count").forGetter(OverloadProcessingIngredient::count))
            .apply(instance, OverloadProcessingIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, OverloadProcessingIngredient> STREAM_CODEC =
            StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC,
                    OverloadProcessingIngredient::ingredient,
                    ByteBufCodecs.VAR_INT,
                    OverloadProcessingIngredient::count,
                    OverloadProcessingIngredient::new);

    public OverloadProcessingIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
