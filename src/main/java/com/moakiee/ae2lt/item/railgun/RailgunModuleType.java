package com.moakiee.ae2lt.item.railgun;

import net.minecraft.util.StringRepresentable;

import com.mojang.serialization.Codec;

public enum RailgunModuleType implements StringRepresentable {
    CORE("core"),
    COMPUTE("compute"),
    ACCELERATION("acceleration"),
    OVERLOAD_EXECUTION("overload_execution"),
    RANGE("range"),
    MULTIDIMENSIONAL_EXECUTION("multidimensional_execution");

    public static final Codec<RailgunModuleType> CODEC = StringRepresentable.fromEnum(RailgunModuleType::values);
    // 1.20.1 has no StreamCodec/ByteBufCodecs (1.21 API); module data crosses the network
    // as a serialized name inside the stack NBT, so no buffer codec is needed here.

    private final String name;

    RailgunModuleType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
