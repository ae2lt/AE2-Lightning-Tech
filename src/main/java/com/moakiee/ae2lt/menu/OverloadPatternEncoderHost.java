package com.moakiee.ae2lt.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.menuobjects.ItemMenuHost;

/**
 * Item-menu host for the hand-held overload pattern encoder.
 * <p>
 * The host only anchors the menu to the held item. All edit state lives in the
 * menu itself.
 */
public final class OverloadPatternEncoderHost extends ItemMenuHost {
    public OverloadPatternEncoderHost(
            Player player,
            int inventorySlot,
            ItemStack stack
    ) {
        super(player, inventorySlot, stack);
    }
}

