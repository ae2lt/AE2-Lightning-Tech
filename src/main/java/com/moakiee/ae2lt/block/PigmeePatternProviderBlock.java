package com.moakiee.ae2lt.block;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class PigmeePatternProviderBlock extends AEBaseEntityBlock<PigmeePatternProviderBlockEntity> {
    public static final EnumProperty<PushDirection> PUSH_DIRECTION =
            EnumProperty.create("push_direction", PushDirection.class);

    public PigmeePatternProviderBlock() {
        super(metalProps().forceSolidOn());
        registerDefaultState(defaultBlockState().setValue(PUSH_DIRECTION, PushDirection.ALL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PUSH_DIRECTION);
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        // 1.20.1 merges the 1.21 useItemOn/useWithoutItem pair into a single use().
        // Wrench rotation wins; everything else defers to AE2's menu opening.
        ItemStack heldItem = player.getItemInHand(hand);
        if (InteractionUtil.canWrenchRotate(heldItem)) {
            setOutputSide(level, pos, hit.getDirection());
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.use(state, level, pos, player, hand, hit);
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
