package com.moakiee.ae2lt.block;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.block.crafting.PushDirection;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import appeng.util.Platform;
import com.moakiee.ae2lt.blockentity.PigmeePatternProviderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class PigmeePatternProviderBlock extends AEBaseEntityBlock<PigmeePatternProviderBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<PushDirection> PUSH_DIRECTION =
            EnumProperty.create("push_direction", PushDirection.class);

    public PigmeePatternProviderBlock() {
        super(metalProps().forceSolidOn());
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(PUSH_DIRECTION, PushDirection.ALL));
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PUSH_DIRECTION);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack heldItem,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (InteractionUtil.canWrenchRotate(heldItem)) {
            setOutputSide(level, pos, hit.getDirection());
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult) {
        var blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            blockEntity.openMenu(player, MenuLocators.forBlockEntity(blockEntity));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private void setOutputSide(Level level, BlockPos pos, Direction clickedSide) {
        var state = level.getBlockState(pos);
        var currentSide = state.getValue(PUSH_DIRECTION).getDirection();

        PushDirection next;
        if (currentSide == clickedSide.getOpposite()) {
            next = PushDirection.fromDirection(clickedSide);
        } else if (currentSide == clickedSide) {
            next = PushDirection.ALL;
        } else if (currentSide == null) {
            next = PushDirection.fromDirection(clickedSide.getOpposite());
        } else {
            next = PushDirection.fromDirection(Platform.rotateAround(currentSide, clickedSide));
        }
        level.setBlockAndUpdate(pos, state.setValue(PUSH_DIRECTION, next));
    }
}
