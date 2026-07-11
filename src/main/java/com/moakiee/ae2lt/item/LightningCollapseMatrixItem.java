package com.moakiee.ae2lt.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;

public class LightningCollapseMatrixItem extends Item {
    public LightningCollapseMatrixItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return insertIntoFactory(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return insertIntoFactory(context);
    }

    private InteractionResult insertIntoFactory(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }

        var level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos())
                instanceof OverloadProcessingFactoryBlockEntity factory)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            factory.insertMatricesFromHand(player, context.getHand());
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
