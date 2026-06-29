package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MatrixFormedBlock extends MatrixMultiblockSimpleBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public MatrixFormedBlock(Properties properties, MatrixMultiblockComponent component) {
        super(properties, component);
        registerDefaultState(defaultBlockState().setValue(FORMED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return usesTransparentFormedModel(state) ? Shapes.empty() : super.getOcclusionShape(state, level, pos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return usesTransparentFormedModel(state) || super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return usesTransparentFormedModel(state) ? 0 : super.getLightBlock(state, level, pos);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return usesTransparentFormedModel(state) ? 1.0F : super.getShadeBrightness(state, level, pos);
    }

    private boolean usesTransparentFormedModel(BlockState state) {
        if (!state.getValue(FORMED)) {
            return false;
        }
        return switch (matrixComponent(state)) {
            case STABLE_MAIN_CORE,
                    QUANTUM_MAIN_CORE,
                    OVERLOAD_MAIN_CORE,
                    BLANK_SUB_CORE,
                    THREAD_SUB_CORE_T1,
                    THREAD_SUB_CORE_T2,
                    MULTIPLIER_SUB_CORE_T1,
                    MULTIPLIER_SUB_CORE_T2,
                    COOLING_SUB_CORE_T1,
                    COOLING_SUB_CORE_T2 -> true;
            default -> false;
        };
    }
}
