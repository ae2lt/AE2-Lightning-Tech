package com.moakiee.ae2lt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class TeslaCoilHalfHelper {
    private TeslaCoilHalfHelper() {
    }

    public static BlockPos getCapabilityHostPos(DoubleBlockHalf half, BlockPos pos) {
        return half == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }
}
