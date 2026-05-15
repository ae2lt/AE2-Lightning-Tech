package com.moakiee.ae2lt.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public interface WirelessConnectionRef {
    ResourceKey<Level> dimension();

    BlockPos pos();

    Direction boundFace();

    CompoundTag toTag();

    default boolean sameTarget(ResourceKey<Level> otherDim, BlockPos otherPos) {
        return dimension().equals(otherDim) && pos().equals(otherPos);
    }
}
