package com.moakiee.ae2lt.item;

import java.util.Set;

import appeng.helpers.patternprovider.PatternContainer;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class OverloadedPatternProviderUpgradeItem extends AbstractPatternProviderUpgradeItem {
    private static final Set<ResourceLocation> SUPPORTED_SOURCE_BLOCKS = Set.of(
            new ResourceLocation("ae2", "pattern_provider"),
            new ResourceLocation("expatternprovider", "ex_pattern_provider"),
            new ResourceLocation("advanced_ae", "small_adv_pattern_provider"),
            new ResourceLocation("advanced_ae", "adv_pattern_provider"));

    public OverloadedPatternProviderUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isSupportedSource(BlockState state, BlockEntity blockEntity) {
        return blockEntity instanceof PatternContainer
                && supportsSource(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    static boolean supportsSource(ResourceLocation blockId) {
        return SUPPORTED_SOURCE_BLOCKS.contains(blockId);
    }

    @Override
    protected Block replacementBlock() {
        return ModBlocks.OVERLOADED_PATTERN_PROVIDER.get();
    }

    @Override
    protected BlockEntity createReplacement(BlockPos pos, BlockState state) {
        return new OverloadedPatternProviderBlockEntity(pos, state);
    }
}
