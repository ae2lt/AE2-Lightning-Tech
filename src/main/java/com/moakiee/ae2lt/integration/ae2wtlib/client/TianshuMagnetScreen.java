package com.moakiee.ae2lt.integration.ae2wtlib.client;

import appeng.client.gui.style.StyleManager;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu.WirelessPage;
import de.mari_023.ae2wtlib.api.gui.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetScreen;

/** Inherits WT's complete magnet UI, widgets, tooltips and resource-pack layout. */
public final class TianshuMagnetScreen extends MagnetScreen {
    private final TianshuEnhancedWirelessCraftingScreen parent;
    private final TianshuEnhancedWirelessCraftingMenu features;

    TianshuMagnetScreen(TianshuEnhancedWirelessCraftingScreen parent) {
        super(parent.features().getMagnetMenu().asClientView(), parent.getMenu().getPlayerInventory(),
                parent.getTitle(), StyleManager.loadStyleDoc("/screens/wtlib/magnet.json"));
        this.parent = parent;
        features = parent.features();
        features.setWirelessPage(WirelessPage.MAGNET);
    }

    public void returnToTerminal() {
        features.setWirelessPage(WirelessPage.MAIN);
        switchToScreen(parent);
    }

    @Override public void onClose() { returnToTerminal(); }
    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (features.wirelessPage != WirelessPage.MAGNET) {
            switchToScreen(parent);
            return;
        }
        for (var slot : menu.slots) {
            var semantic = menu.getSlotSemantic(slot);
            if (semantic != null) setSlotsHidden(semantic,
                    semantic != SlotSemantics.PLAYER_INVENTORY && semantic != SlotSemantics.PLAYER_HOTBAR
                            && semantic != AE2wtlibSlotSemantics.PICKUP_CONFIG && semantic != AE2wtlibSlotSemantics.INSERT_CONFIG);
        }
    }
}
