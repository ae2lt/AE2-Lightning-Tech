package com.moakiee.ae2lt.item;

import appeng.blockentity.AEBaseBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

abstract class AbstractPatternProviderUpgradeItem extends Item {
    protected AbstractPatternProviderUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    public final InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return tryUpgrade(context, stack);
    }

    @Override
    public final InteractionResult useOn(UseOnContext context) {
        return tryUpgrade(context, context.getItemInHand());
    }

    private InteractionResult tryUpgrade(UseOnContext context, ItemStack stack) {
        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }

        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (!canUpgrade(level, pos)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            upgrade(level, pos, stack);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    public final boolean canUpgrade(Level level, BlockPos pos) {
        var originalState = level.getBlockState(pos);
        var originalEntity = level.getBlockEntity(pos);
        return originalEntity != null && isSupportedSource(originalState, originalEntity);
    }

    public final void upgrade(Level level, BlockPos pos, ItemStack stack) {
        if (level.isClientSide() || !canUpgrade(level, pos)) {
            return;
        }

        var originalEntity = level.getBlockEntity(pos);
        var originalState = level.getBlockState(pos);
        var replacementState = copySharedProperties(originalState, replacementBlock().defaultBlockState());
        var replacementEntity = createReplacement(pos, replacementState);
        replaceBlockEntity(level, pos, originalEntity, replacementEntity, replacementState);
        stack.shrink(1);
    }

    protected abstract boolean isSupportedSource(BlockState state, BlockEntity blockEntity);

    protected abstract Block replacementBlock();

    protected abstract BlockEntity createReplacement(BlockPos pos, BlockState state);

    private static void replaceBlockEntity(
            Level level,
            BlockPos pos,
            BlockEntity oldEntity,
            BlockEntity replacementEntity,
            BlockState replacementState) {
        var savedTag = oldEntity.saveWithFullMetadata(level.registryAccess());
        level.removeBlockEntity(pos);
        level.removeBlock(pos, false);
        level.setBlock(pos, replacementState, Block.UPDATE_ALL);
        level.setBlockEntity(replacementEntity);
        replacementEntity.loadWithComponents(savedTag, level.registryAccess());
        if (replacementEntity instanceof AEBaseBlockEntity aeBlockEntity) {
            aeBlockEntity.markForUpdate();
        } else {
            replacementEntity.setChanged();
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static BlockState copySharedProperties(BlockState originalState, BlockState replacementState) {
        var state = replacementState;
        for (var entry : originalState.getValues().entrySet()) {
            Property property = entry.getKey();
            if (state.hasProperty(property)) {
                state = state.setValue(property, (Comparable) entry.getValue());
            }
        }
        return state;
    }
}
