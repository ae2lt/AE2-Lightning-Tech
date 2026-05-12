package com.moakiee.ae2lt.item.railgun;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import com.mojang.serialization.Codec;

public enum RailgunModuleType implements StringRepresentable {
    CORE("core"),
    COMPUTE("compute"),
    ACCELERATION("acceleration"),
    ENERGY("energy"),
    OVERLOAD_EXECUTION("overload_execution");

    public static final Codec<RailgunModuleType> CODEC = StringRepresentable.fromEnum(RailgunModuleType::values);
    public static final StreamCodec<ByteBuf, RailgunModuleType> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], RailgunModuleType::ordinal);

    private final String name;

    RailgunModuleType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
