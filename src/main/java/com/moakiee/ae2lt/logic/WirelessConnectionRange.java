package com.moakiee.ae2lt.logic;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class WirelessConnectionRange {
    private WirelessConnectionRange() {
    }

    public static int maxConnectorDistance() {
        return AE2LTCommonConfig.wirelessConnectorMaxDistance();
    }

    public static boolean isConnectorLinkInRange(Level level, BlockPos hostPos, BlockPos targetPos) {
        return isInRange(level.dimension(), hostPos, level.dimension(), targetPos, maxConnectorDistance());
    }

    public static boolean isConnectorLinkInRange(
            ResourceKey<Level> hostDimension,
            BlockPos hostPos,
            ResourceKey<Level> targetDimension,
            BlockPos targetPos) {
        return isInRange(hostDimension, hostPos, targetDimension, targetPos, maxConnectorDistance());
    }

    private static boolean isInRange(
            ResourceKey<Level> hostDimension,
            BlockPos hostPos,
            ResourceKey<Level> targetDimension,
            BlockPos targetPos,
            int maxDistance) {
        if (!hostDimension.equals(targetDimension)) {
            return false;
        }
        if (maxDistance <= 0) {
            return true;
        }
        long maxDistanceSq = (long) maxDistance * (long) maxDistance;
        return hostPos.distSqr(targetPos) <= maxDistanceSq;
    }
}
