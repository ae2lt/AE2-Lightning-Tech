package com.moakiee.ae2lt.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;

/** AE2LT configuration adapter for Thunderbolt's generic range algorithm. */
public final class WirelessConnectionRange {
    private WirelessConnectionRange() {}

    public static int maxConnectorDistance() {
        return AE2LTCommonConfig.wirelessConnectorMaxDistance();
    }

    public static boolean isConnectorLinkInRange(Level level, BlockPos hostPos, BlockPos targetPos) {
        return isConnectorLinkInRange(level.dimension(), hostPos, level.dimension(), targetPos);
    }

    public static boolean isConnectorLinkInRange(
            ResourceKey<Level> hostDimension,
            BlockPos hostPos,
            ResourceKey<Level> targetDimension,
            BlockPos targetPos) {
        return com.moakiee.thunderbolt.api.wireless.WirelessConnectionRange.isInRange(
                hostDimension, hostPos, targetDimension, targetPos, maxConnectorDistance());
    }
}
