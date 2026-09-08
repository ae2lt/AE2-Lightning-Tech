package com.moakiee.ae2lt.integration.ae2wtlib.client;

import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.client.TianshuWirelessCraftingTermScreen;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu.WirelessPage;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage;
import com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu;
import de.mari_023.ae2wtlib.api.TextConstants;
import de.mari_023.ae2wtlib.api.gui.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.api.gui.Icon;
import de.mari_023.ae2wtlib.api.gui.IconButton;
import de.mari_023.ae2wtlib.wct.ArmorSlot;
import de.mari_023.ae2wtlib.wct.PlayerEntityWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Uses WCT's original crafting layout; optional classes load only with the full WT implementation. */
public final class TianshuEnhancedWirelessCraftingScreen extends TianshuWirelessCraftingTermScreen<TianshuWirelessCraftingTermMenu> {
    private final IconButton settingsButton, magnetButton, trashButton;
    private final PlayerEntityWidget playerPreview;

    public TianshuEnhancedWirelessCraftingScreen(TianshuWirelessCraftingTermMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        settingsButton = new IconButton(button -> switchToScreen(new TianshuWirelessToolsScreen(this, WirelessPage.SETTINGS)), Icon.TERMINAL_SETTINGS);
        settingsButton.setMessage(TextConstants.TERMINAL_SETTINGS);
        widgets.add("wirelessTerminalSettingsButton", settingsButton);
        magnetButton = new IconButton(button -> switchToScreen(new TianshuMagnetScreen(this)), Icon.MAGNET);
        magnetButton.setMessage(TextConstants.MAGNET_FILTER);
        widgets.add("magnetCardMenuButton", magnetButton);
        trashButton = new IconButton(button -> switchToScreen(new TianshuWirelessToolsScreen(this, WirelessPage.TRASH)), Icon.TRASH);
        trashButton.setMessage(TextConstants.TRASH);
        widgets.add("trashButton", trashButton);
        playerPreview = new PlayerEntityWidget(menu.getPlayer());
        widgets.add("player", playerPreview);
    }

    public TianshuEnhancedWirelessCraftingMenu features() { return (TianshuEnhancedWirelessCraftingMenu) menu; }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        settingsButton.setVisibility(true);
        magnetButton.setVisibility(features().hasMagnetCard());
        trashButton.setVisibility(true);
        playerPreview.visible = true;
        for (var semantic : TianshuEnhancedWirelessCraftingMenu.ARMOR_SLOTS) setSlotsHidden(semantic, false);
        setSlotsHidden(AE2wtlibSlotSemantics.PICKUP_CONFIG, true);
        setSlotsHidden(AE2wtlibSlotSemantics.INSERT_CONFIG, true);
        setSlotsHidden(AE2wtlibSlotSemantics.TRASH, true);
    }

    @Override protected void drawWorkAreaBackground(GuiGraphics graphics, int offsetX, int offsetY) {
        // The player, armor, offhand and WT buttons remain unchanged on every work page.
        drawWorkAreaFrame(graphics, offsetX + 89, offsetY + imageHeight - 170, 99, 72);
    }

    @Override public void renderSlot(GuiGraphics graphics, Slot slot) {
        if (slot instanceof ArmorSlot armor && slot.getItem().isEmpty() && armor.isSlotEnabled()) {
            armor.icon().getBlitter().dest(slot.x, slot.y).opacity(armor.getOpacityOfIcon()).blit(graphics);
        }
        super.renderSlot(graphics, slot);
    }
}
