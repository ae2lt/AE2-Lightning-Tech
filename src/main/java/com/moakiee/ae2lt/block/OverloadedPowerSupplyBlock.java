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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;

public class OverloadedPowerSupplyBlock extends AEBaseEntityBlock<OverloadedPowerSupplyBlockEntity> {

    /**
     * Block-state visualisation:
     * <ul>
     *   <li>{@code POWERED=false} → {@code overload_power_supply_off}</li>
     *   <li>{@code POWERED=true, OVERLOADED=false} → {@code overload_power_supply_on}</li>
     *   <li>{@code POWERED=true, OVERLOADED=true} → {@code overload_power_supply_on_overloaded}</li>
     * </ul>
     * The {@code (false, true)} combination is also mapped to the "off" model
     * so that pre-selecting OVERLOAD mode without an active transfer does not
     * leak the overloaded crystal texture.
     */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final BooleanProperty OVERLOADED = BooleanProperty.create("overloaded");
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /**
     * Tight collision/selection shape that follows the Blockbench model
     * elements:
     * <ul>
     *   <li>底座圆台:{@code (2..14, 0..7, 2..14)}(略放大覆盖斜置散热鳍)</li>
     *   <li>晶体支柱:{@code (6..10, 7..14, 6..10)}(含两道装饰环)</li>
     *   <li>顶端尖塔:{@code (7..9, 10..16, 7..9)}</li>
     * </ul>
     * 三块取并集生成最终 shape,与默认满方块的碰撞盒区分开。
     */
    private static final VoxelShape UP_SHAPE = BlockShapeHelper.or(
            Block.box(2, 0, 2, 14, 7, 14),
            Block.box(6, 7, 6, 10, 14, 10),
            Block.box(7, 10, 7, 9, 16, 9));
    private static final EnumMap<Direction, VoxelShape> SHAPES =
            BlockShapeHelper.createAllFacingShapes(UP_SHAPE);

    public OverloadedPowerSupplyBlock() {
        super(metalProps().noOcclusion().forceSolidOn());
        registerDefaultState(defaultBlockState()
                .setValue(POWERED, false)
                .setValue(OVERLOADED, false)
                .setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED, OVERLOADED);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facing();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        var be = this.getBlockEntity(level, pos);
        if (be != null) {
            if (!level.isClientSide()) {
                be.openMenu(player, MenuLocators.forBlockEntity(be));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }
}
