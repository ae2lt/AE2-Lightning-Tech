package com.moakiee.ae2lt.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import com.moakiee.ae2lt.machine.common.LightningCollapseMatrixHost;

public class LightningCollapseMatrixItem extends Item {
    public LightningCollapseMatrixItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return insertIntoMachine(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return insertIntoMachine(context);
    }

    private InteractionResult insertIntoMachine(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }

        var level = context.getLevel();
        var host = LightningCollapseMatrixHost.find(level, context.getClickedPos());
        if (host == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            host.insertMatricesFromHand(player, context.getHand());
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
