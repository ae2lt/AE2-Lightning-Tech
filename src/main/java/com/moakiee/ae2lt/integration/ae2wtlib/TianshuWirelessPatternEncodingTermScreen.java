package com.moakiee.ae2lt.integration.ae2wtlib;

import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.client.TianshuPatternEncodingTermScreen;
import de.mari_023.ae2wtlib.api.gui.ScrollingUpgradesPanel;
import de.mari_023.ae2wtlib.api.terminal.IUniversalTerminalCapable;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class TianshuWirelessPatternEncodingTermScreen
        extends TianshuPatternEncodingTermScreen<TianshuWirelessPatternEncodingTermMenu>
        implements IUniversalTerminalCapable {
    private final ScrollingUpgradesPanel upgradesPanel;

    public TianshuWirelessPatternEncodingTermScreen(
            TianshuWirelessPatternEncodingTermMenu menu, Inventory inventory,
            Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        if (menu.isWUT()) addToLeftToolbar(cycleTerminalButton());
        upgradesPanel = addUpgradePanel(widgets, menu);
    }

    @Override
    public void init() {
        super.init();
        upgradesPanel.setMaxRows(Math.max(2, getVisibleRows()));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        return handled || checkForTerminalKeys(keyCode, scanCode);
    }

    @Override public WTMenuHost getHost() { return (WTMenuHost) menu.getHost(); }
}
