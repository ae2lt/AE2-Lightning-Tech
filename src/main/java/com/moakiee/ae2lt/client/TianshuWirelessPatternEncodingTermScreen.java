package com.moakiee.ae2lt.client;

import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Wireless variant of the Tianshu pattern-encoding terminal screen. */
public final class TianshuWirelessPatternEncodingTermScreen
        extends TianshuPatternEncodingTermScreen<TianshuWirelessPatternEncodingTermMenu> {
    public TianshuWirelessPatternEncodingTermScreen(
            TianshuWirelessPatternEncodingTermMenu menu,
            Inventory inventory,
            Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
    }
}
