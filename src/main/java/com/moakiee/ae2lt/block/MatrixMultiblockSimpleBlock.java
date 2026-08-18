package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockUpdateScheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class MatrixMultiblockSimpleBlock extends Block implements MatrixMultiblockComponentBlock {
    private final MatrixMultiblockComponent component;

    public MatrixMultiblockSimpleBlock(Properties properties, MatrixMultiblockComponent component) {
        super(properties);
        this.component = component;
    }

    @Override
    public MatrixMultiblockComponent matrixComponent(BlockState state) {
        return component;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) {
            MatrixMultiblockUpdateScheduler.scheduleNear(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            MatrixMultiblockUpdateScheduler.scheduleNear(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
        MatrixMultiblockUpdateScheduler.scheduleNear(level, fromPos);
    }
}
