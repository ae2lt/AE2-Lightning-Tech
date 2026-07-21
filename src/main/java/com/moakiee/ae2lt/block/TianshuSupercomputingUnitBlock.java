package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockComponent;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockUpdateScheduler;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockUpdateScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class TianshuSupercomputingUnitBlock extends Block implements MatrixMultiblockComponentBlock {
    public static final BooleanProperty FORMED = TianshuSupercomputerStructureBlock.FORMED;
    private final TianshuMultiblockComponent component;
    private final MatrixMultiblockComponent matrixComponent;

    public TianshuSupercomputingUnitBlock(Properties properties, TianshuMultiblockComponent component) {
        this(properties, component, MatrixMultiblockComponent.OTHER);
    }

    public TianshuSupercomputingUnitBlock(
            Properties properties,
            TianshuMultiblockComponent component,
            MatrixMultiblockComponent matrixComponent) {
        super(properties);
        this.component = component;
        this.matrixComponent = matrixComponent;
        registerDefaultState(defaultBlockState().setValue(FORMED, false));
    }

    public TianshuMultiblockComponent component() {
        return component;
    }

    @Override
    public MatrixMultiblockComponent matrixComponent(BlockState state) {
        return matrixComponent;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) {
            TianshuMultiblockUpdateScheduler.scheduleNear(level, pos);
            scheduleMatrixUpdate(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            TianshuMultiblockUpdateScheduler.scheduleNear(level, pos);
            scheduleMatrixUpdate(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block block,
            BlockPos fromPos,
            boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
        if (matrixComponent != MatrixMultiblockComponent.OTHER) {
            MatrixMultiblockUpdateScheduler.scheduleNear(level, fromPos);
        }
    }

    private void scheduleMatrixUpdate(Level level, BlockPos pos) {
        if (matrixComponent != MatrixMultiblockComponent.OTHER) {
            MatrixMultiblockUpdateScheduler.scheduleNear(level, pos);
        }
    }
}
