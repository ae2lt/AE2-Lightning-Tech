package com.moakiee.ae2lt.logic.wireless.support;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class WirelessConnectionValidator {
    public enum Status { VALID, UNLOADED, REMOVE }

    private WirelessConnectionValidator() {}

    public static boolean shouldRunPeriodicPrune(
            ServerLevel level, BlockPos hostPos, int intervalTicks) {
        if (intervalTicks <= 0) return false;
        int offset = Math.floorMod(hostPos.asLong(), intervalTicks);
        return (level.getGameTime() + offset) % intervalTicks == 0;
    }

    public static Status validate(
            ServerLevel hostLevel,
            BlockPos hostPos,
            WirelessConnectionRef target,
            int maxDistance) {
        return validate(
                hostLevel, hostPos, target.dimension(), target.pos(), maxDistance);
    }

    public static Status validate(
            ServerLevel hostLevel,
            BlockPos hostPos,
            ResourceKey<Level> targetDimension,
            BlockPos targetPos,
            int maxDistance) {
        if (!WirelessConnectionRange.isInRange(
                hostLevel.dimension(), hostPos, targetDimension, targetPos, maxDistance)) {
            return Status.REMOVE;
        }
        var targetLevel = hostLevel.getServer().getLevel(targetDimension);
        if (targetLevel == null) return Status.REMOVE;
        if (!targetLevel.isLoaded(targetPos)) return Status.UNLOADED;
        var state = targetLevel.getBlockState(targetPos);
        return state.isAir() || targetLevel.getBlockEntity(targetPos) == null
                ? Status.REMOVE
                : Status.VALID;
    }
}
