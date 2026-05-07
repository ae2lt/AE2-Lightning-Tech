package com.moakiee.ae2lt.block;

import java.util.EnumMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;

public class CrystalCatalyzerBlock extends AEBaseEntityBlock<CrystalCatalyzerBlockEntity> {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    // Horizontal-only: the model is axially symmetric around the vertical axis,
    // and the block's output side config UI (RelativeSide.TOP) should always map
    // to world UP so players can reason about output faces intuitively.
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape UPRIGHT_SHAPE = BlockShapeHelper.or(
            Block.box(0, 0, 0, 16, 3, 16),
            Block.box(0, 13, 0, 16, 16, 16),
            Block.box(0, 3, 0, 3, 13, 3),
            Block.box(0, 3, 13, 3, 13, 16),
            Block.box(13, 3, 0, 16, 13, 3),
            Block.box(13, 3, 13, 16, 13, 16),
            Block.box(4, 3, 4, 12, 6, 12),
            Block.box(4, 10, 4, 12, 13, 12));
    private static final EnumMap<Direction, VoxelShape> SHAPES =
            BlockShapeHelper.createHorizontalFacingShapes(UPRIGHT_SHAPE);

    public CrystalCatalyzerBlock() {
        super(metalProps().noOcclusion().forceSolidOn());
        registerDefaultState(defaultBlockState()
                .setValue(WORKING, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WORKING);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block block, BlockPos fromPos, boolean isMoving) {
        var be = getBlockEntity(level, pos);
        if (be != null) {
            be.onNeighborChanged(fromPos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        var be = getBlockEntity(level, pos);
        if (be == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            be.openMenu(player, MenuLocators.forBlockEntity(be));
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
