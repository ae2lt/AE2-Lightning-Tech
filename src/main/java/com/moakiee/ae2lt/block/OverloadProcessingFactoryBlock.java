package com.moakiee.ae2lt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import org.jetbrains.annotations.Nullable;

public class OverloadProcessingFactoryBlock extends AEBaseEntityBlock<OverloadProcessingFactoryBlockEntity> {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public OverloadProcessingFactoryBlock() {
        super(metalProps(Properties.of()).noOcclusion().forceSolidOn());
        registerDefaultState(defaultBlockState()
                .setValue(WORKING, false)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
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
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
            Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        var be = getBlockEntity(level, pos);
        if (be != null) {
            be.onNeighborChanged(null);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, net.minecraft.core.BlockPos pos, Player player, BlockHitResult hitResult) {
        var be = getBlockEntity(level, pos);
        if (be == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            be.openMenu(player, MenuLocators.forBlockEntity(be));
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack heldItem,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (heldItem.getItem() instanceof BucketItem) {
            if (useBucket(player, level, pos, hand, hit)) {
                return InteractionResult.SUCCESS;
            }
        }

        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
    }

    private boolean useBucket(Player player, Level level, BlockPos pos, InteractionHand hand, BlockHitResult hit) {
        var blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null) {
            return false;
        }
        return FluidUtil.interactWithFluidHandler(
                player,
                hand,
                pos,
                blockEntity.getFluidHandlerCapability(hit.getDirection()));
    }
}

