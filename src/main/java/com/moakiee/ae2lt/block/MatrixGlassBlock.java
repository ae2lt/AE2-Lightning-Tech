package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Matrix glass block. Carries a FORMED state so the client connected-texture
 * model can switch to the assembled appearance once the multiblock forms.
 * The controller updates this state on form/deform.
 */
public class MatrixGlassBlock extends MatrixFormedBlock {
    public static final BooleanProperty FORMED = MatrixFormedBlock.FORMED;

    public MatrixGlassBlock(Properties properties, MatrixMultiblockComponent component) {
        super(properties, component);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        return adjacentState.getBlock() instanceof MatrixGlassBlock;
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
