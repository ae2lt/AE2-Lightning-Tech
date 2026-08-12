package com.moakiee.ae2lt.client;

import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import de.mari_023.ae2wtlib.wut.CycleTerminalButton;
import de.mari_023.ae2wtlib.wut.IUniversalTerminalCapable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Wireless variant of the Tianshu pattern-encoding terminal screen. */
public final class TianshuWirelessPatternEncodingTermScreen
        extends TianshuPatternEncodingTermScreen<TianshuWirelessPatternEncodingTermMenu>
        implements IUniversalTerminalCapable {
    public TianshuWirelessPatternEncodingTermScreen(
            TianshuWirelessPatternEncodingTermMenu menu,
            Inventory inventory,
            Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        if (menu.isWUT()) {
            addToLeftToolbar(new CycleTerminalButton(ignored -> cycleTerminal()));
        }
    }
}
