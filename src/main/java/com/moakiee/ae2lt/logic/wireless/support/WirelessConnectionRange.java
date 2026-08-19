package com.moakiee.ae2lt.logic.wireless.support;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class WirelessConnectionRange {
    private WirelessConnectionRange() {}

    public static boolean isInRange(
            ResourceKey<Level> hostDimension,
            BlockPos hostPos,
            ResourceKey<Level> targetDimension,
            BlockPos targetPos,
            int maxDistance) {
        if (!hostDimension.equals(targetDimension)) return false;
        if (maxDistance <= 0) return true;
        long maxDistanceSquared = (long) maxDistance * maxDistance;
        return hostPos.distSqr(targetPos) <= maxDistanceSquared;
    }
}
