package com.moakiee.ae2lt.menu;

import net.minecraft.world.entity.player.Player;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.locator.ItemMenuHostLocator;

import com.moakiee.ae2lt.item.OverloadPatternEncoderItem;

/**
 * Item-menu host for the hand-held overload pattern encoder.
 * <p>
 * The host only anchors the menu to the held item. All edit state lives in the
 * menu itself.
 */
public final class OverloadPatternEncoderHost extends ItemMenuHost<OverloadPatternEncoderItem> {
    public OverloadPatternEncoderHost(
            OverloadPatternEncoderItem item,
            Player player,
            ItemMenuHostLocator locator
    ) {
        super(item, player, locator);
    }
}
