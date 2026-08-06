package com.moakiee.ae2lt.logic;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** @deprecated Use Thunderbolt's reusable target scanner. */
@Deprecated(forRemoval = false)
public final class WirelessConnectorTargetHelper {
    private WirelessConnectorTargetHelper() {}

    public static Set<BlockPos> collectTargets(Level level, BlockPos origin, boolean contiguous) {
        return com.moakiee.thunderbolt.api.wireless.WirelessConnectorTargetHelper
                .collectTargets(level, origin, contiguous);
    }

    public static Set<BlockPos> collectTargets(
            Level level, BlockPos origin, boolean contiguous, int maxTargets) {
        return com.moakiee.thunderbolt.api.wireless.WirelessConnectorTargetHelper
                .collectTargets(level, origin, contiguous, maxTargets);
    }
}
