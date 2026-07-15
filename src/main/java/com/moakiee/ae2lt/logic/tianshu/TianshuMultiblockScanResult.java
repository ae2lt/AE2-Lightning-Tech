package com.moakiee.ae2lt.logic.tianshu;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record TianshuMultiblockScanResult(
        BlockPos controllerPos,
        Direction orientation,
        BlockPos minPos,
        BlockPos maxPos,
        BlockPos portPos,
        List<BlockPos> members,
        List<BlockPos> corePositions,
        List<BlockPos> patternStoragePositions,
        List<BlockPos> seedStoragePositions,
        CpuInternalCoreProfile coreProfile,
        TianshuFunctionProfile functionProfile) {
}
