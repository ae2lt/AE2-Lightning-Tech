package com.moakiee.ae2lt.client.hub;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.hub.DeviceHubDisplayRules;
import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.network.hub.DeviceHubActionPacket;

public class DeviceHubScreen extends AbstractContainerScreen<DeviceHubMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/armor_settings_gui.png");

    private static final int TEXTURE_SIZE = 256;
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 223;

    private static final int TEXT_PRIMARY = 0xFF6E748C;
    private static final int TEXT_SECONDARY = 0xFF7D839B;
    private static final int TEXT_OK = 0xFF5F8F78;
    private static final int TEXT_WARN = 0xFF9A8064;
    private static final int TEXT_DISABLED = 0xFF666B80;
    private static final int TEXT_ACCENT = 0xFF9A8A5B;
    private static final int ROW_HOVER = 0x304D4D67;
    private static final int ROW_SELECTED = 0x404D4D67;
    private static final int TOGGLE_ON = 0xFF6F927C;
    private static final int TOGGLE_OFF = 0xFF6B7086;
    private static final int BUTTON_BORDER = 0xFF8E8465;
    private static final int BUTTON_DISABLED = 0xFF6B7086;
    private static final int BUTTON_FILL = 0xFF69708A;
    private static final int BUTTON_FILL_DISABLED = 0xFF7D839B;
    private static final int BUTTON_TEXT = 0xFFCCD2DE;

    private static final int TAB_COUNT = 5;
    private static final int TAB_Y = 0;
    private static final int TAB_WIDTH = 31;
    private static final int TAB_HEIGHT = 25;
    private static final int TAB_TEXT_WIDTH = 25;
    private static final int TAB_ACTIVE_SRC_Y = 225;
    private static final int TAB_ACTIVE_H = 26;
    private static final int[] TAB_X = {0, 31, 62, 93, 145};
    private static final int[] TAB_ACTIVE_SRC_X = {0, 31, 62, 93, 145};

    private static final int STATUS_X = 12;
    private static final int STATUS_Y = 36;
    private static final int STATUS_RIGHT = 166;

    private static final int MODULE_HEADER_X = 12;
    private static final int MODULE_HEADER_Y = 70;
    private static final int MODULE_LIST_X = 19;
    private static final int MODULE_LIST_Y = 83;
    private static final int MODULE_LIST_RIGHT = 166;
    private static final int MODULE_ROW_H = 14;
    private static final int MODULE_VISIBLE_ROWS = 4;
    private static final int MODULE_TOGGLE_X = 134;

    private static final int SCROLL_X = 10;
    private static final int SCROLL_Y = 83;
    private static final int SCROLL_H = 56;
    private static final int SCROLL_SRC_X = 180;
    private static final int SCROLL_SRC_Y = 0;
    private static final int SCROLL_SRC_W = 7;
    private static final int SCROLL_SRC_H = 15;

    private static final int CONFIG_X = 12;
    private static final int CONFIG_Y = 156;
    private static final int CONFIG_BUTTON_X = 108;
    private static final int CONFIG_BUTTON_W = 56;
    private static final int CONFIG_BUTTON_H = 12;

    private static final int TOGGLE_W = 30;
    private static final int TOGGLE_H = 12;

    private static final String[] TAB_LABEL_KEYS = {
            "ae2lt.device_hub.tab.helmet",
            "ae2lt.device_hub.tab.chestplate",
            "ae2lt.device_hub.tab.leggings",
            "ae2lt.device_hub.tab.boots",
            "ae2lt.device_hub.tab.railgun"
    };
    private static final String[] TAB_REQUIRED_KEYS = {
            "ae2lt.device_hub.tab.required.helmet",
            "ae2lt.device_hub.tab.required.chestplate",
            "ae2lt.device_hub.tab.required.leggings",
            "ae2lt.device_hub.tab.required.boots",
            "ae2lt.device_hub.tab.required.railgun"
    };

    private int scrollOffset = 0;

    public DeviceHubScreen(DeviceHubMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelY = this.imageHeight + 100;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 0;
        this.titleLabelY = -100;
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        gfx.blit(TEXTURE, leftPos, topPos, 0, 0, GUI_WIDTH, GUI_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        renderSelectedTabTexture(gfx, menu.getSelectedTab());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);

        int selectedTab = menu.getSelectedTab();
        int tabMask = menu.getTabAvailability();
        boolean railgunTab = selectedTab == DeviceHubMenu.TAB_RAILGUN;

        renderTabLabels(gfx, selectedTab, tabMask);

        boolean hasDevice = (tabMask & (1 << selectedTab)) != 0;
        if (!hasDevice) {
            gfx.drawString(font, Component.translatable("ae2lt.device_hub.no_device"),
                    leftPos + STATUS_X, topPos + STATUS_Y + 9, TEXT_SECONDARY, false);
            renderTabTooltips(gfx, mouseX, mouseY, tabMask);
            renderTooltip(gfx, mouseX, mouseY);
            return;
        }

        renderStatusPanel(gfx, railgunTab);
        renderModuleList(gfx, mouseX, mouseY, railgunTab);

        if (railgunTab) {
            renderRailgunSettings(gfx);
        } else {
            renderModuleConfig(gfx, leftPos + CONFIG_X, topPos + CONFIG_Y);
        }

        gfx.drawString(font, Component.translatable("ae2lt.device_hub.workbench_hint"),
                leftPos + CONFIG_X, topPos + GUI_HEIGHT - 14, TEXT_SECONDARY, false);

        renderTabTooltips(gfx, mouseX, mouseY, tabMask);
        renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
    }

    private void renderSelectedTabTexture(GuiGraphics gfx, int selectedTab) {
        if (selectedTab < 0 || selectedTab >= TAB_COUNT) {
            return;
        }
        gfx.blit(TEXTURE,
                leftPos + TAB_X[selectedTab],
                topPos + TAB_Y,
                TAB_ACTIVE_SRC_X[selectedTab],
                TAB_ACTIVE_SRC_Y,
                TAB_WIDTH,
                TAB_ACTIVE_H,
                TEXTURE_SIZE,
                TEXTURE_SIZE);
    }

    private void renderTabLabels(GuiGraphics gfx, int selectedTab, int tabMask) {
        for (int i = 0; i < TAB_COUNT; i++) {
            int x = leftPos + TAB_X[i];
            boolean available = (tabMask & (1 << i)) != 0;
            boolean active = i == selectedTab;
            int color = active ? TEXT_PRIMARY : available ? TEXT_SECONDARY : TEXT_DISABLED;
            String label = truncate(font, Component.translatable(TAB_LABEL_KEYS[i]).getString(), TAB_TEXT_WIDTH);
            int textX = x + (TAB_WIDTH - font.width(label)) / 2;
            gfx.drawString(font, Component.literal(label), textX, topPos + 8, color, false);
        }
    }

    private void renderStatusPanel(GuiGraphics gfx, boolean railgunTab) {
        int x = leftPos + STATUS_X;
        int y = topPos + STATUS_Y;

        String deviceName = menu.getDeviceName();
        if (!deviceName.isEmpty()) {
            gfx.drawString(font, Component.literal(truncate(font, deviceName, STATUS_RIGHT - STATUS_X)),
                    x, y, TEXT_PRIMARY, false);
        }

        String boundDim = menu.getBoundDim();
        boolean gridReachable = menu.isGridReachable();
        Component binding = boundDim.isEmpty()
                ? Component.translatable("ae2lt.device_hub.ap.unbound")
                : Component.translatable(
                        gridReachable
                                ? "ae2lt.device_hub.ap.bound.reachable"
                                : "ae2lt.device_hub.ap.bound.unreachable",
                        boundDim);
        gfx.drawString(font, binding, x, y + 11, gridReachable ? TEXT_OK : TEXT_SECONDARY, false);

        boolean appFlux = menu.isAppFluxOnline();
        Component flux = Component.translatable(
                appFlux ? "ae2lt.device_hub.appflux.online" : "ae2lt.device_hub.appflux.missing");
        gfx.drawString(font, flux, x, y + 22, appFlux ? TEXT_OK : TEXT_WARN, false);

        Component statusText = statusText(railgunTab);
        int statusColor = statusColor(railgunTab);
        gfx.drawString(font, statusText,
                leftPos + STATUS_RIGHT - font.width(statusText), y + 22, statusColor, false);
    }

    private Component statusText(boolean railgunTab) {
        if (!railgunTab) {
            return Component.translatable(DeviceHubDisplayRules.armorStatusKey(
                    menu.hasCore(),
                    menu.isPowered()));
        }
        return Component.translatable(menu.isPowered()
                ? "ae2lt.device_hub.status.normal"
                : "ae2lt.device_hub.status.unpowered");
    }

    private int statusColor(boolean railgunTab) {
        if (!railgunTab && !menu.hasCore()) {
            return TEXT_WARN;
        }
        return menu.isPowered() ? TEXT_OK : TEXT_WARN;
    }

    private void renderModuleList(GuiGraphics gfx, int mouseX, int mouseY, boolean railgunTab) {
        List<String> moduleNameKeys = menu.getModuleNameKeys();
        List<Integer> moduleCounts = menu.getModuleCounts();
        List<Integer> moduleCooldowns = menu.getModuleCooldowns();
        List<Boolean> moduleEnabled = menu.getModuleEnabled();
        List<Boolean> moduleActive = menu.getModuleActive();
        int moduleCount = DeviceHubDisplayRules.countModuleUnits(moduleCounts);

        gfx.drawString(font, Component.translatable("ae2lt.device_hub.modules", moduleCount, menu.getModuleSlotCount()),
                leftPos + MODULE_HEADER_X, topPos + MODULE_HEADER_Y, TEXT_PRIMARY, false);

        scrollOffset = DeviceHubDisplayRules.clampScrollOffset(
                scrollOffset, moduleNameKeys.size(), MODULE_VISIBLE_ROWS);
        int selectedModuleIndex = menu.getSelectedModuleIndex();
        for (int i = 0; i < Math.min(moduleNameKeys.size(), MODULE_VISIBLE_ROWS); i++) {
            int idx = i + scrollOffset;
            if (idx >= moduleNameKeys.size()) {
                break;
            }

            int rowY = topPos + MODULE_LIST_Y + i * MODULE_ROW_H;
            boolean hovered = mouseX >= leftPos + MODULE_LIST_X
                    && mouseX < leftPos + MODULE_LIST_RIGHT
                    && mouseY >= rowY
                    && mouseY < rowY + MODULE_ROW_H;
            if (hovered || (!railgunTab && idx == selectedModuleIndex)) {
                gfx.fill(leftPos + MODULE_LIST_X - 1, rowY - 1,
                        leftPos + MODULE_LIST_RIGHT, rowY + MODULE_ROW_H - 1,
                        idx == selectedModuleIndex ? ROW_SELECTED : ROW_HOVER);
            }

            int count = idx < moduleCounts.size() ? moduleCounts.get(idx) : 1;
            gfx.drawString(font, moduleName(moduleNameKeys.get(idx), count),
                    leftPos + MODULE_LIST_X, rowY + 2, TEXT_PRIMARY, false);

            if (!railgunTab) {
                boolean enabled = idx < moduleEnabled.size() && moduleEnabled.get(idx);
                boolean active = idx < moduleActive.size() && moduleActive.get(idx);
                int cooldown = idx < moduleCooldowns.size() ? moduleCooldowns.get(idx) : 0;
                Component stateLine = moduleStateLine(enabled, active, cooldown);
                int stateX = leftPos + MODULE_TOGGLE_X - font.width(stateLine) - 4;
                gfx.drawString(font, stateLine, stateX, rowY + 2, TEXT_SECONDARY, false);
                drawToggleButton(gfx, leftPos + MODULE_TOGGLE_X, rowY + 1, enabled, TOGGLE_ON);
            }
        }

        if (moduleNameKeys.size() > MODULE_VISIBLE_ROWS) {
            renderScrollBar(gfx, moduleNameKeys.size());
        }
    }

    private Component moduleStateLine(boolean enabled, boolean active, int cooldown) {
        Component stateLabel = Component.translatable(DeviceHubDisplayRules.moduleStateKey(enabled, active));
        if (cooldown <= 0) {
            return stateLabel;
        }
        return Component.translatable(
                "ae2lt.device_hub.module.state_cooldown",
                stateLabel,
                (cooldown + 19) / 20);
    }

    private void renderScrollBar(GuiGraphics gfx, int moduleCount) {
        int thumbRange = SCROLL_H - SCROLL_SRC_H;
        int thumbY = topPos + SCROLL_Y + thumbRange * scrollOffset / Math.max(1, moduleCount - MODULE_VISIBLE_ROWS);
        gfx.blit(TEXTURE,
                leftPos + SCROLL_X,
                thumbY,
                SCROLL_SRC_X,
                SCROLL_SRC_Y,
                SCROLL_SRC_W,
                SCROLL_SRC_H,
                TEXTURE_SIZE,
                TEXTURE_SIZE);
    }

    private void renderModuleConfig(GuiGraphics gfx, int x, int y) {
        int count = moduleConfigCount();
        if (count <= 0) {
            gfx.drawString(font, Component.translatable("ae2lt.overload_armor.screen.module_options"),
                    x, y, TEXT_SECONDARY, false);
            return;
        }

        gfx.drawString(font, Component.translatable("ae2lt.overload_armor.screen.module_options"),
                x, y, TEXT_PRIMARY, false);
        int rowY = y + 14;
        for (int i = 0; i < Math.min(count, 2); i++) {
            String value = menu.getModuleConfigValues().get(i);
            boolean editable = menu.getModuleConfigEditable().get(i);
            gfx.drawString(font, Component.literal("  ").append(moduleConfigLabel(i)),
                    x, rowY + 1, TEXT_PRIMARY, false);
            drawConfigValueButton(gfx, leftPos + CONFIG_BUTTON_X, rowY - 1, value, editable);
            rowY += MODULE_ROW_H;
        }
    }

    private void renderRailgunSettings(GuiGraphics gfx) {
        int x = leftPos + CONFIG_X;
        int y = topPos + CONFIG_Y;
        gfx.drawString(font, Component.translatable("ae2lt.device_hub.settings"), x, y, TEXT_PRIMARY, false);

        int rowY = y + 16;
        drawSettingRow(gfx, x, rowY,
                Component.translatable("ae2lt.device_hub.setting.terrain"),
                menu.isTerrainDestruction(),
                menu.isTerrainDestructionAllowed() ? 0xFF9A6C70 : TOGGLE_OFF);
        rowY += MODULE_ROW_H + 2;
        drawSettingRow(gfx, x, rowY,
                Component.translatable("ae2lt.device_hub.setting.pvp_lock"),
                menu.isPvpLock(),
                0xFF6E7FA2);
    }

    private void drawSettingRow(GuiGraphics gfx, int x, int y, Component label, boolean on, int onColor) {
        gfx.drawString(font, Component.literal("  ").append(label), x, y + 1, TEXT_PRIMARY, false);
        drawToggleButton(gfx, leftPos + MODULE_TOGGLE_X, y, on, onColor);
    }

    private void drawToggleButton(GuiGraphics gfx, int x, int y, boolean on, int onColor) {
        int borderColor = on ? onColor : TOGGLE_OFF;
        int fillColor = on ? darken(onColor) : BUTTON_FILL_DISABLED;
        gfx.fill(x - 1, y - 1, x + TOGGLE_W + 1, y + TOGGLE_H + 1, borderColor);
        gfx.fill(x, y, x + TOGGLE_W, y + TOGGLE_H, fillColor);
        String text = on ? "ON" : "OFF";
        int textColor = on ? BUTTON_TEXT : TEXT_SECONDARY;
        gfx.drawString(font, Component.literal(text),
                x + (TOGGLE_W - font.width(text)) / 2, y + 2, textColor, false);
    }

    private void drawConfigValueButton(GuiGraphics gfx, int x, int y, String value, boolean editable) {
        int borderColor = editable ? BUTTON_BORDER : BUTTON_DISABLED;
        int fillColor = editable ? BUTTON_FILL : BUTTON_FILL_DISABLED;
        gfx.fill(x - 1, y - 1, x + CONFIG_BUTTON_W + 1, y + CONFIG_BUTTON_H + 1, borderColor);
        gfx.fill(x, y, x + CONFIG_BUTTON_W, y + CONFIG_BUTTON_H, fillColor);
        String text = truncate(font, value, CONFIG_BUTTON_W - 4);
        int textColor = editable ? BUTTON_TEXT : TEXT_SECONDARY;
        gfx.drawString(font, Component.literal(text),
                x + (CONFIG_BUTTON_W - font.width(text)) / 2, y + 2, textColor, false);
    }

    private boolean mouseClickedModuleConfig(double mouseX, double mouseY) {
        int count = moduleConfigCount();
        if (count <= 0) {
            return false;
        }
        int rowY = topPos + CONFIG_Y + 14;
        int buttonX = leftPos + CONFIG_BUTTON_X;
        for (int i = 0; i < Math.min(count, 2); i++) {
            boolean editable = menu.getModuleConfigEditable().get(i);
            if (editable
                    && mouseX >= buttonX
                    && mouseX <= buttonX + CONFIG_BUTTON_W
                    && mouseY >= rowY - 1
                    && mouseY <= rowY - 1 + CONFIG_BUTTON_H) {
                PacketDistributor.sendToServer(new DeviceHubActionPacket(
                        DeviceHubActionPacket.ACTION_CYCLE_MODULE_CONFIG, i));
                return true;
            }
            rowY += MODULE_ROW_H;
        }
        return false;
    }

    private int moduleConfigCount() {
        return Math.min(
                Math.min(Math.min(menu.getModuleConfigKeys().size(), menu.getModuleConfigLabels().size()),
                        menu.getModuleConfigValues().size()),
                Math.min(menu.getModuleConfigKinds().size(), menu.getModuleConfigEditable().size()));
    }

    private Component moduleConfigLabel(int index) {
        String key = menu.getModuleConfigKeys().get(index);
        if (key != null && !key.isBlank()) {
            return Component.translatable("ae2lt.overload_armor.config." + key);
        }
        return Component.literal(menu.getModuleConfigLabels().get(index));
    }

    private static Component moduleName(String nameKey, int count) {
        Component name = Component.translatable(nameKey);
        if (count > 1) {
            return Component.translatable("ae2lt.device_hub.module.counted", name, count);
        }
        return Component.literal("  ").append(name);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int tabMask = menu.getTabAvailability();
        int selectedTab = menu.getSelectedTab();

        for (int i = 0; i < TAB_COUNT; i++) {
            int tx = leftPos + TAB_X[i];
            int ty = topPos + TAB_Y;
            if (mouseX >= tx && mouseX <= tx + TAB_WIDTH && mouseY >= ty && mouseY <= ty + TAB_HEIGHT) {
                if ((tabMask & (1 << i)) != 0 && i != selectedTab) {
                    PacketDistributor.sendToServer(new DeviceHubActionPacket(
                            DeviceHubActionPacket.ACTION_SELECT_TAB, i));
                    playClick();
                }
                return true;
            }
        }

        if (selectedTab != DeviceHubMenu.TAB_RAILGUN) {
            if (mouseClickedModuleConfig(mouseX, mouseY)) {
                playClick();
                return true;
            }
            if (mouseClickedArmorModule(mouseX, mouseY)) {
                playClick();
                return true;
            }
        } else if (mouseClickedRailgunSettings(mouseX, mouseY)) {
            playClick();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean mouseClickedArmorModule(double mouseX, double mouseY) {
        List<String> moduleNames = menu.getModuleNameKeys();
        scrollOffset = DeviceHubDisplayRules.clampScrollOffset(
                scrollOffset, moduleNames.size(), MODULE_VISIBLE_ROWS);

        for (int i = 0; i < Math.min(moduleNames.size(), MODULE_VISIBLE_ROWS); i++) {
            int idx = i + scrollOffset;
            int rowY = topPos + MODULE_LIST_Y + i * MODULE_ROW_H;
            int toggleX = leftPos + MODULE_TOGGLE_X;
            if (mouseX >= toggleX
                    && mouseX <= toggleX + TOGGLE_W
                    && mouseY >= rowY + 1
                    && mouseY <= rowY + 1 + TOGGLE_H) {
                PacketDistributor.sendToServer(new DeviceHubActionPacket(
                        DeviceHubActionPacket.ACTION_TOGGLE_MODULE, idx));
                return true;
            }
            if (mouseX >= leftPos + MODULE_LIST_X
                    && mouseX <= leftPos + MODULE_LIST_RIGHT
                    && mouseY >= rowY
                    && mouseY <= rowY + MODULE_ROW_H) {
                PacketDistributor.sendToServer(new DeviceHubActionPacket(
                        DeviceHubActionPacket.ACTION_SELECT_MODULE, idx));
                return true;
            }
        }
        return false;
    }

    private boolean mouseClickedRailgunSettings(double mouseX, double mouseY) {
        int toggleX = leftPos + MODULE_TOGGLE_X;
        int toggleY = topPos + CONFIG_Y + 16;
        if (mouseX >= toggleX && mouseX <= toggleX + TOGGLE_W) {
            if (mouseY >= toggleY && mouseY <= toggleY + TOGGLE_H) {
                PacketDistributor.sendToServer(new DeviceHubActionPacket(
                        DeviceHubActionPacket.ACTION_TOGGLE_TERRAIN, 0));
                return true;
            }
            toggleY += MODULE_ROW_H + 2;
            if (mouseY >= toggleY && mouseY <= toggleY + TOGGLE_H) {
                PacketDistributor.sendToServer(new DeviceHubActionPacket(
                        DeviceHubActionPacket.ACTION_TOGGLE_PVP, 0));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258) {
            cycleTab((modifiers & 1) != 0 ? -1 : 1);
            return true;
        }
        if (keyCode == 263) {
            cycleTab(-1);
            return true;
        }
        if (keyCode == 262) {
            cycleTab(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < leftPos + 8
                || mouseX > leftPos + 168
                || mouseY < topPos + 78
                || mouseY > topPos + 142) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        List<String> moduleNames = menu.getModuleNameKeys();
        if (scrollY > 0 && scrollOffset > 0) {
            scrollOffset--;
        } else if (scrollY < 0) {
            scrollOffset++;
        }
        scrollOffset = DeviceHubDisplayRules.clampScrollOffset(
                scrollOffset, moduleNames.size(), MODULE_VISIBLE_ROWS);
        return true;
    }

    private void renderTabTooltips(GuiGraphics gfx, int mouseX, int mouseY, int tabMask) {
        for (int i = 0; i < TAB_COUNT; i++) {
            int tx = leftPos + TAB_X[i];
            boolean available = (tabMask & (1 << i)) != 0;
            if (!available
                    && mouseX >= tx
                    && mouseX <= tx + TAB_WIDTH
                    && mouseY >= topPos + TAB_Y
                    && mouseY <= topPos + TAB_Y + TAB_HEIGHT) {
                gfx.renderTooltip(font, Component.translatable(TAB_REQUIRED_KEYS[i]), mouseX, mouseY);
                return;
            }
        }
    }

    private void cycleTab(int dir) {
        int current = menu.getSelectedTab();
        int tabMask = menu.getTabAvailability();
        for (int attempt = 0; attempt < TAB_COUNT; attempt++) {
            current = (current + dir + TAB_COUNT) % TAB_COUNT;
            if ((tabMask & (1 << current)) != 0) {
                PacketDistributor.sendToServer(new DeviceHubActionPacket(
                        DeviceHubActionPacket.ACTION_SELECT_TAB, current));
                playClick();
                return;
            }
        }
    }

    private void playClick() {
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width("...");
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - ellipsisWidth)) + "...";
    }

    private static int darken(int argb) {
        int a = argb >>> 24;
        int r = (int) (((argb >> 16) & 0xFF) * 0.45);
        int g = (int) (((argb >> 8) & 0xFF) * 0.45);
        int b = (int) ((argb & 0xFF) * 0.45);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
