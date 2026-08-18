package com.moakiee.ae2lt.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** @deprecated Use Thunderbolt's reusable wireless endpoint contract. */
@Deprecated(forRemoval = false)
public interface WirelessConnectionRef
        extends com.moakiee.thunderbolt.api.wireless.WirelessConnectionRef {
    default boolean sameTarget(
            ResourceKey<Level> otherDimension, BlockPos otherPos) {
        return dimension().equals(otherDimension) && pos().equals(otherPos);
    }
}
