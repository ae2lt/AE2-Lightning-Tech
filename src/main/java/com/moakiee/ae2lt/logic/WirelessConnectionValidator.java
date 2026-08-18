package com.moakiee.ae2lt.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class WirelessConnectionValidator {
    public static final int PERIODIC_PRUNE_INTERVAL_TICKS = 100;
    public static final int PERIODIC_PRUNE_MAX_CHECKS = 64;

    public enum Status {
        VALID,
        UNLOADED,
        REMOVE
    }

    private WirelessConnectionValidator() {
    }

    public static boolean shouldRunPeriodicPrune(ServerLevel level, BlockPos hostPos) {
        int offset = Math.floorMod(hostPos.asLong(), PERIODIC_PRUNE_INTERVAL_TICKS);
        return (level.getGameTime() + offset) % PERIODIC_PRUNE_INTERVAL_TICKS == 0;
    }

    public static Status validate(
            ServerLevel hostLevel,
            BlockPos hostPos,
            WirelessConnectionRef target) {
        return validate(hostLevel, hostPos, target.dimension(), target.pos());
    }

    public static Status validate(
            ServerLevel hostLevel,
            BlockPos hostPos,
            ResourceKey<Level> targetDimension,
            BlockPos targetPos) {
        if (!targetDimension.equals(hostLevel.dimension())) {
            return Status.REMOVE;
        }
        if (!WirelessConnectionRange.isConnectorLinkInRange(
                hostLevel.dimension(), hostPos, targetDimension, targetPos)) {
            return Status.REMOVE;
        }

        var targetLevel = hostLevel.getServer().getLevel(targetDimension);
        if (targetLevel == null) {
            return Status.REMOVE;
        }
        if (!targetLevel.isLoaded(targetPos)) {
            return Status.UNLOADED;
        }

        var state = targetLevel.getBlockState(targetPos);
        if (state.isAir() || targetLevel.getBlockEntity(targetPos) == null) {
            return Status.REMOVE;
        }
        return Status.VALID;
    }
}
