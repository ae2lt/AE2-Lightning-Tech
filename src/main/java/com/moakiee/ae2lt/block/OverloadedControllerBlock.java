package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import appeng.block.AEBaseEntityBlock;
import appeng.block.networking.ControllerBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.networktool.NetworkStatusMenu;

/**
 * Minimal custom controller block shell.
 * Keeps controller-like blockstate properties on our own block so later channel
 * logic can remain owner-scoped instead of patching the vanilla controller.
 * <p>
 * Important: this block only handles AE2LT's own controller visuals and
 * right-click menu entry. It does not alter vanilla AE2 controller behavior.
 */
public class OverloadedControllerBlock extends AEBaseEntityBlock<OverloadedControllerBlockEntity> {

    public OverloadedControllerBlock() {
        super(metalProps().forceSolidOn().strength(6.0F));
        registerDefaultState(defaultBlockState()
                .setValue(ControllerBlock.CONTROLLER_STATE, ControllerBlock.ControllerBlockState.offline)
                .setValue(ControllerBlock.CONTROLLER_TYPE, ControllerBlock.ControllerRenderType.block));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ControllerBlock.CONTROLLER_STATE, ControllerBlock.CONTROLLER_TYPE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return updateControllerType(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level,
            BlockPos pos, BlockPos facingPos) {
        return updateControllerType(state, level, pos);
    }

    private BlockState updateControllerType(BlockState baseState, LevelAccessor level, BlockPos pos) {
        // Only connect render-state to AE2LT's own controller block instances.
        var type = ControllerBlock.ControllerRenderType.block;

        var x = pos.getX();
        var y = pos.getY();
        var z = pos.getZ();

        var xx = isOverloadedController(level, x - 1, y, z) && isOverloadedController(level, x + 1, y, z);
        var yy = isOverloadedController(level, x, y - 1, z) && isOverloadedController(level, x, y + 1, z);
        var zz = isOverloadedController(level, x, y, z - 1) && isOverloadedController(level, x, y, z + 1);

        if (xx && !yy && !zz) {
            type = ControllerBlock.ControllerRenderType.column_x;
        } else if (!xx && yy && !zz) {
            type = ControllerBlock.ControllerRenderType.column_y;
        } else if (!xx && !yy && zz) {
            type = ControllerBlock.ControllerRenderType.column_z;
        } else if ((xx ? 1 : 0) + (yy ? 1 : 0) + (zz ? 1 : 0) >= 2) {
            type = (Math.abs(x) + Math.abs(y) + Math.abs(z)) % 2 == 0
                    ? ControllerBlock.ControllerRenderType.inside_a
                    : ControllerBlock.ControllerRenderType.inside_b;
        }

        return baseState.setValue(ControllerBlock.CONTROLLER_TYPE, type);
    }

    private boolean isOverloadedController(LevelAccessor level, int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof OverloadedControllerBlock;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof OverloadedControllerBlockEntity be) {
            if (!level.isClientSide) {
                // AE2 1.21.1 uses NetworkStatusMenu.CONTROLLER_TYPE for controller right-click.
                // If menu/locator names differ in another version, verify this hook first.
                // This only adds the same network-status entry point to AE2LT's controller
                // and does not modify vanilla controller interaction.
                MenuOpener.open(NetworkStatusMenu.CONTROLLER_TYPE, player, MenuLocators.forBlockEntity(be));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useWithoutItem(state, level, pos, player, hitResult);
    }
}
