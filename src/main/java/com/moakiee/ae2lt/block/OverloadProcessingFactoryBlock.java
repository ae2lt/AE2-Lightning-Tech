package com.moakiee.ae2lt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;

public class OverloadProcessingFactoryBlock extends AEBaseEntityBlock<OverloadProcessingFactoryBlockEntity> {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public OverloadProcessingFactoryBlock() {
        super(metalProps().noOcclusion().forceSolidOn());
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
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block block, BlockPos fromPos, boolean isMoving) {
        var be = getBlockEntity(level, pos);
        if (be != null) {
            be.onNeighborChanged(fromPos);
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

        return InteractionResult.sidedSuccess(level.isClientSide());
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
        if (heldItem.getItem() instanceof BucketItem) {
            if (useBucket(player, level, pos, heldItem, hand)) {
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
        }

        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
    }

    private boolean useBucket(Player player, Level level, BlockPos pos, ItemStack stack, InteractionHand hand) {
        var itemFluid = stack.getCapability(Capabilities.FluidHandler.ITEM);
        var blockFluid = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (itemFluid == null || blockFluid == null) {
            return false;
        }

        if (itemFluid.getFluidInTank(0).isEmpty()) {
            var extracted = blockFluid.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.SIMULATE);
            if (extracted.isEmpty() || extracted.getAmount() != FluidType.BUCKET_VOLUME) {
                return false;
            }

            blockFluid.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.EXECUTE);
            if (itemFluid.getContainer().getCount() == 1) {
                itemFluid.fill(extracted, IFluidHandler.FluidAction.EXECUTE);
                player.setItemInHand(hand, itemFluid.getContainer());
            } else {
                var newBucket = new ItemStack(Items.BUCKET, 1);
                var newBucketFluid = newBucket.getCapability(Capabilities.FluidHandler.ITEM);
                if (newBucketFluid == null) {
                    return false;
                }
                newBucketFluid.fill(extracted, IFluidHandler.FluidAction.EXECUTE);
                player.setItemInHand(hand, newBucketFluid.getContainer());
                player.addItem(new ItemStack(stack.getItem(), stack.getCount() - 1));
            }

            playBucketSound(player, level, pos, extracted, true);
            return true;
        }

        var drained = itemFluid.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.SIMULATE);
        if (drained.isEmpty()) {
            return false;
        }
        int inserted = blockFluid.fill(drained, IFluidHandler.FluidAction.SIMULATE);
        if (inserted != FluidType.BUCKET_VOLUME) {
            return false;
        }

        drained = itemFluid.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.EXECUTE);
        blockFluid.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        player.setItemInHand(hand, itemFluid.getContainer());
        playBucketSound(player, level, pos, drained, false);
        return true;
    }

    private void playBucketSound(Player player, Level level, BlockPos pos, FluidStack fluid, boolean fillBucket) {
        SoundEvent sound = fluid.getFluidType().getSound(
                player,
                level,
                pos,
                fillBucket
                        ? SoundActions.BUCKET_FILL
                        : SoundActions.BUCKET_EMPTY);
        if (sound == null) {
            sound = fillBucket
                    ? (fluid.is(FluidTags.LAVA) ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL)
                    : (fluid.is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY);
        }
        level.playSound(player, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}

