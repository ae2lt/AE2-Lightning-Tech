package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.MatrixPortBlockEntity;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockUpdateScheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import appeng.block.AEBaseEntityBlock;

public class MatrixPortBlock extends AEBaseEntityBlock<MatrixPortBlockEntity>
        implements MatrixMultiblockComponentBlock {
    public static final BooleanProperty FORMED = MatrixFormedBlock.FORMED;

    public MatrixPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FORMED, Boolean.FALSE));
    }

    @Override
    public MatrixMultiblockComponent matrixComponent(BlockState state) {
        return MatrixMultiblockComponent.MATRIX_PORT;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED);
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
