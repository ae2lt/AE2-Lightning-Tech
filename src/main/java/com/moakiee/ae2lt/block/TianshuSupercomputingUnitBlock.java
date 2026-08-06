package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockComponent;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockUpdateScheduler;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockUpdateScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

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
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return usesHiddenFormedModel(state) ? Shapes.empty() : super.getOcclusionShape(state, level, pos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return usesHiddenFormedModel(state) || super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return usesHiddenFormedModel(state) ? 0 : super.getLightBlock(state, level, pos);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return usesHiddenFormedModel(state) ? 1.0F : super.getShadeBrightness(state, level, pos);
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

    private boolean usesHiddenFormedModel(BlockState state) {
        if (!state.getValue(FORMED)) {
            return false;
        }
        return switch (component) {
            case MAIN_BASELINE,
                    MAIN_QUANTUM,
                    MAIN_OVERLOAD,
                    MAIN_MULTIDIMENSIONAL,
                    BLANK_UNIT,
                    STORAGE_UNIT,
                    PARALLEL_UNIT,
                    AMPLIFIER_UNIT -> true;
            default -> false;
        };
    }
}
