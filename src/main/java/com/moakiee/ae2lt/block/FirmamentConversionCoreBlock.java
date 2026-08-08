package com.moakiee.ae2lt.block;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

import com.moakiee.ae2lt.blockentity.FirmamentConversionCoreBlockEntity;
import com.moakiee.ae2lt.registry.ModBlockEntities;

public class FirmamentConversionCoreBlock extends Block implements EntityBlock {
    public FirmamentConversionCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
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
        // An empty hand extracts the finished conversion result; a held item is fed in.
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            if (!(level.getBlockEntity(pos) instanceof FirmamentConversionCoreBlockEntity be)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide()) {
                be.extractToPlayer(player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!(level.getBlockEntity(pos) instanceof FirmamentConversionCoreBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && !be.insertHeldItem(player, hand)) {
            return InteractionResult.PASS;
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FirmamentConversionCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || blockEntityType != ModBlockEntities.FIRMAMENT_CONVERSION_CORE.get()) {
            return null;
        }
        return (l, p, s, be) ->
                FirmamentConversionCoreBlockEntity.serverTick(l, p, s, (FirmamentConversionCoreBlockEntity) be);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof FirmamentConversionCoreBlockEntity be) {
            be.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, builder));
        BlockEntity blockEntity = builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof FirmamentConversionCoreBlockEntity be) {
            be.addDrops(drops);
        }
        return drops;
    }
}
