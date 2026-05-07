package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import appeng.block.AEBaseEntityBlock;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import appeng.util.Platform;

/**
 * Overloaded Pattern Provider block.
 * <p>
 * Reuses AE2's PUSH_DIRECTION blockstate, wrench rotation and redstone update
 * behavior, while binding to its own BlockEntity / Menu.
 * <p>
 * Orientation rules:
 * <ul>
 *   <li>PUSH_DIRECTION is always present and wrench-rotatable in both modes.</li>
 *   <li>In NORMAL mode, PUSH_DIRECTION controls adjacent machine interaction
 *       (same as vanilla Pattern Provider).</li>
 *   <li>In WIRELESS mode, PUSH_DIRECTION is kept for visual / grid-connectivity
 *       purposes only; wireless dispatch and auto-return targets are determined
 *       by the wireless connector's "machine + bound face" connection records,
 *       NOT by this block's orientation.</li>
 * </ul>
 */
public class OverloadedPatternProviderBlock extends AEBaseEntityBlock<OverloadedPatternProviderBlockEntity> {

    public OverloadedPatternProviderBlock() {
        super(metalProps().forceSolidOn());
        registerDefaultState(defaultBlockState().setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.ALL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PatternProviderBlock.PUSH_DIRECTION);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block block, BlockPos fromPos, boolean isMoving) {
        var be = this.getBlockEntity(level, pos);
        if (be != null) {
            be.getLogic().updateRedstoneState();
            be.onNeighborChanged();
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (InteractionUtil.canWrenchRotate(heldItem)) {
            setSide(level, pos, hit.getDirection());
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
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

    public void setSide(Level level, BlockPos pos, Direction facing) {
        var currentState = level.getBlockState(pos);
        var pushSide = currentState.getValue(PatternProviderBlock.PUSH_DIRECTION).getDirection();

        PushDirection newPushDirection;
        if (pushSide == facing.getOpposite()) {
            newPushDirection = PushDirection.fromDirection(facing);
        } else if (pushSide == facing) {
            newPushDirection = PushDirection.ALL;
        } else if (pushSide == null) {
            newPushDirection = PushDirection.fromDirection(facing.getOpposite());
        } else {
            newPushDirection = PushDirection.fromDirection(Platform.rotateAround(pushSide, facing));
        }

        level.setBlockAndUpdate(pos, currentState.setValue(PatternProviderBlock.PUSH_DIRECTION, newPushDirection));
        var be = this.getBlockEntity(level, pos);
        if (be != null) {
            be.onNeighborChanged();
        }
    }
}
