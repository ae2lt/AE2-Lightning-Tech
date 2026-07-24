package com.moakiee.ae2lt.item;

import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import appeng.menu.locator.ItemMenuHostLocator;

/** Wireless item counterpart of the in-world Tianshu pattern encoding terminal. */
public final class TianshuWirelessPatternEncodingTerminalItem extends ItemWT {
    private static final String DESCRIPTION_ID =
            "item.ae2lt.wireless_tianshu_pattern_encoding_terminal";

    @Override
    public MenuType<?> getMenuType(ItemMenuHostLocator locator, Player player) {
        return TianshuWirelessPatternEncodingTermMenu.TYPE;
    }

    @Override
    public String getDescriptionId() {
        return DESCRIPTION_ID;
    }
}
