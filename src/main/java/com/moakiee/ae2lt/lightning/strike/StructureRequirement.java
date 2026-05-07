package com.moakiee.ae2lt.lightning.strike;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;

public record StructureRequirement(BlockPos offset, Block block, boolean consume) {
    public static final Codec<StructureRequirement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    BlockPos.CODEC.fieldOf("offset").forGetter(StructureRequirement::offset),
                    BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(StructureRequirement::block),
                    Codec.BOOL.optionalFieldOf("consume", false).forGetter(StructureRequirement::consume))
            .apply(instance, StructureRequirement::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StructureRequirement> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            StructureRequirement::offset,
            ByteBufCodecs.registry(net.minecraft.core.registries.Registries.BLOCK),
            StructureRequirement::block,
            ByteBufCodecs.BOOL,
            StructureRequirement::consume,
            StructureRequirement::new);
}
