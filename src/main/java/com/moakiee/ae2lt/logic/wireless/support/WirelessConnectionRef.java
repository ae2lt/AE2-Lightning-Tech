package com.moakiee.ae2lt.logic.wireless.support;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Minimal persistent endpoint contract used by Thunderbolt's one-to-many connection utilities. */
public interface WirelessConnectionRef {
    ResourceKey<Level> dimension();

    BlockPos pos();

    Direction boundFace();

    CompoundTag toTag();

    default boolean sameTarget(ResourceKey<Level> otherDimension, BlockPos otherPos) {
        return dimension().equals(otherDimension) && pos().equals(otherPos);
    }
}
