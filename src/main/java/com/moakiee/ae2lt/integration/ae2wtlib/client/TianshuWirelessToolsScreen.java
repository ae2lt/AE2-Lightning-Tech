package com.moakiee.ae2lt.integration.ae2wtlib.client;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu.WirelessPage;
import com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu;
import de.mari_023.ae2wtlib.AE2wtlibAdditionalComponents;
import de.mari_023.ae2wtlib.api.AE2wtlibComponents;
import de.mari_023.ae2wtlib.api.TextConstants;
import de.mari_023.ae2wtlib.api.gui.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.networking.TerminalSettingsPacket;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** WT's settings/trash layouts bound to the current terminal container, preserving all work inputs. */
final class TianshuWirelessToolsScreen extends AESubScreen<TianshuWirelessCraftingTermMenu, TianshuEnhancedWirelessCraftingScreen> {
    private final TianshuEnhancedWirelessCraftingMenu features;
    private final WirelessPage page;

    TianshuWirelessToolsScreen(TianshuEnhancedWirelessCraftingScreen parent, WirelessPage page) {
        super(parent, switch (page) {
            case SETTINGS -> "/screens/wtlib/wireless_terminal_settings.json";
            case TRASH -> "/screens/wtlib/trash.json";
            default -> throw new IllegalArgumentException("Not a settings/trash page: " + page);
        });
        this.page = page;
        features = parent.features();
        features.setWirelessPage(page);
        widgets.add("back", new TabButton(Icon.BACK, menu.getHost().getMainMenuIcon().getHoverName(), b -> returnToParent()));
        if (page == WirelessPage.SETTINGS) addSettings();
    }

    private void addSettings() {
        var pickBlock = widgets.addCheckbox("pickBlock", TextConstants.PICK_BLOCK, null);
        var craftIfMissing = widgets.addCheckbox("craftIfMissing", TextConstants.CRAFT_IF_MISSING, null);
        var restock = widgets.addCheckbox("restock", TextConstants.RESTOCK, null);
        var magnet = widgets.addCheckbox("magnet", TextConstants.MAGNET, null);
        var pickupToME = widgets.addCheckbox("pickupToME", TextConstants.PICKUP_TO_ME, null);
        var stack = menu.getWirelessHost().getItemStack();
        pickBlock.setSelected(stack.getOrDefault(AE2wtlibComponents.PICK_BLOCK, false));
        craftIfMissing.setSelected(stack.getOrDefault(AE2wtlibComponents.CRAFT_IF_MISSING, false));
        craftIfMissing.active = pickBlock.isSelected();
        restock.setSelected(stack.getOrDefault(AE2wtlibComponents.RESTOCK, false));
        var mode = stack.getOrDefault(AE2wtlibAdditionalComponents.MAGNET_SETTINGS, MagnetMode.OFF);
        magnet.setSelected(mode.magnet());
        pickupToME.setSelected(mode.pickupToME());
        magnet.active = pickupToME.active = features.hasMagnetCard();
        Runnable save = () -> {
            craftIfMissing.active = pickBlock.isSelected();
            var locator = menu.getWirelessHost().getLocator();
            if (locator != null) PacketDistributor.sendToServer(new TerminalSettingsPacket(locator,
                    pickBlock.isSelected(), restock.isSelected(), magnet.isSelected(), pickupToME.isSelected(), craftIfMissing.isSelected()));
        };
        for (var checkbox : java.util.List.of(pickBlock, craftIfMissing, restock, magnet, pickupToME)) checkbox.setChangeListener(save);
    }

    @Override protected void onReturnToParent() { features.setWirelessPage(WirelessPage.MAIN); }
    @Override protected boolean shouldAddToolbar() { return false; }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { returnToParent(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        for (var slot : menu.slots) {
            var semantic = menu.getSlotSemantic(slot);
            if (semantic != null) setSlotsHidden(semantic, page != WirelessPage.TRASH
                    || semantic != SlotSemantics.PLAYER_INVENTORY && semantic != SlotSemantics.PLAYER_HOTBAR
                            && semantic != AE2wtlibSlotSemantics.TRASH);
        }
    }

    @Override public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        if (page == WirelessPage.TRASH) setTextContent("title", TextConstants.TRASH.copy().append(" · ")
                .append(Component.translatable("ae2lt.tianshu.wireless.trash_notice")));
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
    }
}
