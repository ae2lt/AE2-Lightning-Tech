package com.moakiee.ae2lt.item;

import com.moakiee.ae2lt.blockentity.ExtendedOverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ExtendedOverloadedPatternProviderUpgradeItem extends AbstractPatternProviderUpgradeItem {
    public ExtendedOverloadedPatternProviderUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isSupportedSource(BlockState state, BlockEntity blockEntity) {
        return state.is(ModBlocks.OVERLOADED_PATTERN_PROVIDER.get())
                && blockEntity instanceof OverloadedPatternProviderBlockEntity
                && !(blockEntity instanceof ExtendedOverloadedPatternProviderBlockEntity);
    }

    @Override
    protected Block replacementBlock() {
        return ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get();
    }

    @Override
    protected BlockEntity createReplacement(BlockPos pos, BlockState state) {
        return new ExtendedOverloadedPatternProviderBlockEntity(pos, state);
    }
}
