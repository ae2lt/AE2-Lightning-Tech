package com.moakiee.ae2lt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public class TianshuSupercomputerGlassBlock extends TianshuSupercomputerStructureBlock {
    public TianshuSupercomputerGlassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        boolean facesHiddenCore = state.getValue(FORMED)
                && adjacentState.getBlock() instanceof TianshuSupercomputingUnitBlock
                && adjacentState.hasProperty(TianshuSupercomputingUnitBlock.FORMED)
                && adjacentState.getValue(TianshuSupercomputingUnitBlock.FORMED);
        return adjacentState.is(this)
                || facesHiddenCore
                || super.skipRendering(state, adjacentState, side);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }
}
