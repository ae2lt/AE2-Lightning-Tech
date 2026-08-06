package com.moakiee.ae2lt.item;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.menu.OverloadPatternEncoderHost;
import com.moakiee.ae2lt.menu.OverloadPatternEncoderMenu;

/**
 * Hand-held configuration entry for creating and editing overload patterns.
 * <p>
 * This item only opens the editor UI. It does not execute pattern semantics.
 */
public class OverloadPatternEncoderItem extends AE2LTItem implements IMenuItem {
    public OverloadPatternEncoderItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public @Nullable OverloadPatternEncoderHost getMenuHost(
            Player player,
            int inventorySlot,
            ItemStack stack,
            @Nullable BlockPos pos
    ) {
        return new OverloadPatternEncoderHost(player, inventorySlot, stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            MenuOpener.open(OverloadPatternEncoderMenu.TYPE, player, MenuLocators.forHand(player, hand));
        }
        return new InteractionResultHolder<>(
                InteractionResult.sidedSuccess(level.isClientSide()),
                player.getItemInHand(hand));
    }
}


