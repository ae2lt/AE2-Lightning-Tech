package com.moakiee.ae2lt.machine.firmament.recipe;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

public record FirmamentConversionIngredient(Ingredient ingredient, int count) {
    private static final Codec<Integer> POSITIVE_COUNT_CODEC = Codec.intRange(1, Integer.MAX_VALUE);

    public static final MapCodec<FirmamentConversionIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(FirmamentConversionIngredient::ingredient),
                    POSITIVE_COUNT_CODEC.fieldOf("count").forGetter(FirmamentConversionIngredient::count))
            .apply(instance, FirmamentConversionIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FirmamentConversionIngredient> STREAM_CODEC =
            StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC,
                    FirmamentConversionIngredient::ingredient,
                    ByteBufCodecs.VAR_INT,
                    FirmamentConversionIngredient::count,
                    FirmamentConversionIngredient::new);

    public FirmamentConversionIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
