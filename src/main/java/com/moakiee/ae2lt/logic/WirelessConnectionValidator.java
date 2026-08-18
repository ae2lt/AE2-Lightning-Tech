package com.moakiee.ae2lt.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** AE2LT policy adapter for Thunderbolt's reusable endpoint validator. */
public final class WirelessConnectionValidator {
    public static final int PERIODIC_PRUNE_INTERVAL_TICKS = 100;
    public static final int PERIODIC_PRUNE_MAX_CHECKS = 64;

    public enum Status { VALID, UNLOADED, REMOVE }

    private WirelessConnectionValidator() {}

    public static boolean shouldRunPeriodicPrune(ServerLevel level, BlockPos hostPos) {
        return com.moakiee.thunderbolt.api.wireless.WirelessConnectionValidator
                .shouldRunPeriodicPrune(level, hostPos, PERIODIC_PRUNE_INTERVAL_TICKS);
    }

    public static Status validate(
            ServerLevel hostLevel, BlockPos hostPos, WirelessConnectionRef target) {
        return convert(com.moakiee.thunderbolt.api.wireless.WirelessConnectionValidator.validate(
                hostLevel, hostPos, target, WirelessConnectionRange.maxConnectorDistance()));
    }

    public static Status validate(
            ServerLevel hostLevel,
            BlockPos hostPos,
            ResourceKey<Level> targetDimension,
            BlockPos targetPos) {
        return convert(com.moakiee.thunderbolt.api.wireless.WirelessConnectionValidator.validate(
                hostLevel,
                hostPos,
                targetDimension,
                targetPos,
                WirelessConnectionRange.maxConnectorDistance()));
    }

    private static Status convert(
            com.moakiee.thunderbolt.api.wireless.WirelessConnectionValidator.Status status) {
        return Status.valueOf(status.name());
    }
}
