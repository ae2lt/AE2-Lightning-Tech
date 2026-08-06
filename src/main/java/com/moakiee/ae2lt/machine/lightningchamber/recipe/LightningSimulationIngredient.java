package com.moakiee.ae2lt.machine.lightningchamber.recipe;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

public record LightningSimulationIngredient(Ingredient ingredient, int count) {
    private static final Codec<Integer> POSITIVE_COUNT_CODEC = Codec.intRange(1, Integer.MAX_VALUE);

    public static final MapCodec<LightningSimulationIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(LightningSimulationIngredient::ingredient),
                    POSITIVE_COUNT_CODEC.fieldOf("count").forGetter(LightningSimulationIngredient::count))
            .apply(instance, LightningSimulationIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LightningSimulationIngredient> STREAM_CODEC =
            StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC,
                    LightningSimulationIngredient::ingredient,
                    ByteBufCodecs.VAR_INT,
                    LightningSimulationIngredient::count,
                    LightningSimulationIngredient::new);

    public LightningSimulationIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
