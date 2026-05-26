package com.moakiee.ae2lt.client.hub;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.network.hub.DeviceHubActionPacket;

/**
 * Unified device hub screen — tabs for 4 armor pieces + railgun.
 * Pure code-drawn UI (no texture files).
 */
public class DeviceHubScreen extends AbstractContainerScreen<DeviceHubMenu> {

    // ── Colors (spec Appendix C) ──
    private static final int BG_DEEP = 0xFF1E1E1E;
    private static final int BG_LIGHT = 0xFF313131;
    private static final int HIGHLIGHT_GOLD = 0xFFF6D365;
    private static final int ENERGY_GREEN = 0xFF36B65C;
    private static final int LOAD_GOLD = 0xFFF6D365;
    private static final int LOCK_RED = 0xFFC24848;
    private static final int FLUX_ONLINE = 0xFF36B65C;
    private static final int FLUX_MISSING = 0xFFFFAA00;
    private static final int TAB_CURRENT = 0xFFF6D365;
    private static final int TAB_DISABLED = 0xFF555555;
    private static final int TEXT_PRIMARY = 0xFFE0E0E0;
    private static final int TEXT_SECONDARY = 0xFF8B8B8B;
    private static final int WARNING_RED = 0xFFFF6060;

    // ── Layout constants ──
    private static final int TAB_COUNT = 5;
    private static final int TAB_WIDTH = 44;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_GAP = 2;
    private static final int TAB_Y = 4;

    private static final int STATUS_Y = 24;
    private static final int ENERGY_BAR_Y = 64;
    private static final int LOAD_BAR_Y = 84;
    private static final int STATE_LINE_Y = 100;
    private static final int MODULES_Y = 116;
    private static final int MODULE_ROW_H = 14;
    private static final int BAR_WIDTH = 180;
    private static final int BAR_HEIGHT = 8;
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
        this.imageWidth = 240;
        this.imageHeight = 220;
        this.inventoryLabelY = this.imageHeight + 100; // hide it
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // Outer dark border
        gfx.fill(x, y, x + imageWidth, y + imageHeight, BG_DEEP);
        // Inner panel
        gfx.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, BG_LIGHT);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);

        int x = leftPos + 8;
        int y = topPos;

        int selectedTab = menu.getSelectedTab();
        int tabMask = menu.getTabAvailability();

        // ── Tab bar ──
        renderTabBar(gfx, leftPos + 8, topPos + TAB_Y, selectedTab, tabMask);

        // ── Separator ──
        gfx.fill(leftPos + 6, topPos + TAB_Y + TAB_HEIGHT + 2, leftPos + imageWidth - 6, topPos + TAB_Y + TAB_HEIGHT + 3, BG_DEEP);

        // ── Check if device available for current tab ──
        boolean hasDevice = (tabMask & (1 << selectedTab)) != 0;
        if (!hasDevice) {
            gfx.drawString(font, Component.translatable("ae2lt.device_hub.no_device"), x, topPos + STATUS_Y, TEXT_SECONDARY, false);
            renderTooltip(gfx, mouseX, mouseY);
            return;
        }

        // ── Device name ──
        String deviceName = menu.getDeviceName();
        if (!deviceName.isEmpty()) {
            gfx.drawString(font, Component.literal(deviceName), x, topPos + STATUS_Y, TEXT_PRIMARY, false);
        }

        // ── Binding ──
        String boundDim = menu.getBoundDim();
        boolean gridReachable = menu.data.get(DeviceHubMenu.DATA_GRID_REACHABLE) != 0;
        int bx = 0, by = 0, bz = 0; // Not exposed in data slots; use boundDim presence
        if (!boundDim.isEmpty()) {
            int bindColor = gridReachable ? FLUX_ONLINE : WARNING_RED;
            gfx.drawString(font, Component.translatable(
                            gridReachable
                                    ? "ae2lt.device_hub.ap.bound.reachable"
                                    : "ae2lt.device_hub.ap.bound.unreachable",
                            boundDim),
                    x, topPos + STATUS_Y + 12, bindColor, false);
        } else {
            gfx.drawString(font, Component.translatable("ae2lt.device_hub.ap.unbound"), x, topPos + STATUS_Y + 12, TEXT_SECONDARY, false);
        }

        // ── AppFlux ──
        boolean appFlux = menu.data.get(DeviceHubMenu.DATA_APP_FLUX_ONLINE) != 0;
        int fluxColor = appFlux ? FLUX_ONLINE : FLUX_MISSING;
        gfx.drawString(font, Component.translatable(
                        appFlux ? "ae2lt.device_hub.appflux.online" : "ae2lt.device_hub.appflux.missing"),
                x, topPos + STATUS_Y + 24, fluxColor, false);

        // ── Separator ──
        gfx.fill(leftPos + 6, topPos + ENERGY_BAR_Y - 4, leftPos + imageWidth - 6, topPos + ENERGY_BAR_Y - 3, BG_DEEP);

        // ── Energy bar ──
        long stored = menu.getEnergyStored();
        long capacity = menu.getEnergyCapacity();
        gfx.drawString(font, Component.translatable("ae2lt.device_hub.energy"), x, topPos + ENERGY_BAR_Y - 1, TEXT_PRIMARY, false);
        int barX = x + 30;
        drawBar(gfx, barX, topPos + ENERGY_BAR_Y, BAR_WIDTH, BAR_HEIGHT,
                capacity > 0 ? (double) stored / capacity : 0, ENERGY_GREEN);
        String energyText = formatEnergy(stored) + " / " + formatEnergy(capacity) + " FE";
        gfx.drawString(font, Component.literal(energyText), barX + BAR_WIDTH + 4, topPos + ENERGY_BAR_Y, TEXT_SECONDARY, false);

        // ── Load bar ──
        int load = menu.data.get(DeviceHubMenu.DATA_DYNAMIC_LOAD);
        int cap = menu.data.get(DeviceHubMenu.DATA_OVERLOAD_CAP);
        gfx.drawString(font, Component.translatable("ae2lt.device_hub.load"), x, topPos + LOAD_BAR_Y - 1, TEXT_PRIMARY, false);
        int loadColor = load > cap ? LOCK_RED : LOAD_GOLD;
        drawBar(gfx, barX, topPos + LOAD_BAR_Y, BAR_WIDTH, BAR_HEIGHT,
                cap > 0 ? Math.min(1.0, (double) load / cap) : 0, loadColor);
        String loadText = load + " / " + cap;
        gfx.drawString(font, Component.literal(loadText), barX + BAR_WIDTH + 4, topPos + LOAD_BAR_Y, TEXT_SECONDARY, false);

        // ── Status line ──
        int lockState = menu.data.get(DeviceHubMenu.DATA_LOCK_STATE);
        int lockValue = menu.data.get(DeviceHubMenu.DATA_LOCK_VALUE);
        boolean powered = menu.data.get(DeviceHubMenu.DATA_POWERED) != 0;
        Component statusText;
        int statusColor;
        if (lockState == 2) {
            statusText = Component.translatable("ae2lt.device_hub.status.locked", lockValue / 20);
            statusColor = LOCK_RED;
        } else if (lockState == 1) {
            statusText = Component.translatable("ae2lt.device_hub.status.overloaded", lockValue);
            statusColor = FLUX_MISSING;
        } else if (!powered) {
            statusText = Component.translatable("ae2lt.device_hub.status.unpowered");
            statusColor = FLUX_MISSING;
        } else {
            statusText = Component.translatable("ae2lt.device_hub.status.normal");
            statusColor = FLUX_ONLINE;
        }
        gfx.drawString(font, Component.translatable("ae2lt.device_hub.status.line", statusText), x, topPos + STATE_LINE_Y, statusColor, false);

        // ── Separator ──
        gfx.fill(leftPos + 6, topPos + MODULES_Y - 4, leftPos + imageWidth - 6, topPos + MODULES_Y - 3, BG_DEEP);

        // ── Module list ──
        List<String> moduleNameKeys = menu.getModuleNameKeys();
        List<Integer> moduleCounts = menu.getModuleCounts();
        List<Integer> moduleLoads = menu.getModuleLoads();
        int moduleCount = menu.data.get(DeviceHubMenu.DATA_MODULE_COUNT);
        int moduleSlotCount = menu.data.get(DeviceHubMenu.DATA_MODULE_SLOT_COUNT);
        int moduleMask = menu.data.get(DeviceHubMenu.DATA_MODULE_MASK);

        gfx.drawString(font, Component.translatable("ae2lt.device_hub.modules", moduleCount, moduleSlotCount),
                x, topPos + MODULES_Y, TEXT_PRIMARY, false);

        int moduleListY = topPos + MODULES_Y + 14;
        int maxVisible = (topPos + imageHeight - 30 - moduleListY) / MODULE_ROW_H;
        for (int i = 0; i < Math.min(moduleNameKeys.size(), maxVisible); i++) {
            int idx = i + scrollOffset;
            if (idx >= moduleNameKeys.size()) break;

            int rowY = moduleListY + i * MODULE_ROW_H;
            boolean enabled = (moduleMask & (1 << idx)) != 0;
            int count = idx < moduleCounts.size() ? moduleCounts.get(idx) : 1;
            int moduleLoad = idx < moduleLoads.size() ? moduleLoads.get(idx) : 0;

            // Module name
            gfx.drawString(font, moduleName(moduleNameKeys.get(idx), count), x, rowY, TEXT_PRIMARY, false);
            Component loadLabel = Component.translatable("ae2lt.device_hub.module.load", moduleLoad);
            int loadTextX = selectedTab != DeviceHubMenu.TAB_RAILGUN
                    ? leftPos + imageWidth - 56 - font.width(loadLabel)
                    : leftPos + imageWidth - 12 - font.width(loadLabel);
            gfx.drawString(font, loadLabel, loadTextX, rowY, moduleLoad > 0 ? LOAD_GOLD : TEXT_SECONDARY, false);

            // Toggle button (only for armor modules, not railgun)
            if (selectedTab != DeviceHubMenu.TAB_RAILGUN) {
                int toggleX = leftPos + imageWidth - 48;
                drawToggleButton(gfx, toggleX, rowY - 1, enabled);
            }
        }

        // ── Railgun settings toggles ──
        if (selectedTab == DeviceHubMenu.TAB_RAILGUN) {
            int toggleY = moduleListY + Math.min(moduleNameKeys.size(), maxVisible) * MODULE_ROW_H + 8;
            boolean terrain = menu.data.get(DeviceHubMenu.DATA_RAILGUN_TERRAIN) != 0;
            boolean terrainAllowed = menu.data.get(DeviceHubMenu.DATA_RAILGUN_TERRAIN_ALLOWED) != 0;
            boolean pvp = menu.data.get(DeviceHubMenu.DATA_RAILGUN_PVP) != 0;

            gfx.fill(leftPos + 6, toggleY - 4, leftPos + imageWidth - 6, toggleY - 3, BG_DEEP);
            gfx.drawString(font, Component.translatable("ae2lt.device_hub.settings"), x, toggleY, TEXT_PRIMARY, false);
            toggleY += 14;

            drawSettingRow(gfx, x, toggleY, Component.translatable("ae2lt.device_hub.setting.terrain"), terrain, terrainAllowed ? 0xFFCC4444 : TAB_DISABLED);
            toggleY += MODULE_ROW_H + 2;
            drawSettingRow(gfx, x, toggleY, Component.translatable("ae2lt.device_hub.setting.pvp_lock"), pvp, 0xFF4488CC);
        }

        // ── Bottom hint ──
        gfx.drawString(font, Component.translatable("ae2lt.device_hub.workbench_hint"),
                x, topPos + imageHeight - 14, TEXT_SECONDARY, false);

        // ── Tooltips ──
        renderTabTooltips(gfx, mouseX, mouseY, leftPos + 8, topPos + TAB_Y, tabMask);
        renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(this.font, Component.translatable("ae2lt.device_hub.title"), this.titleLabelX, this.titleLabelY, TEXT_PRIMARY, false);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = -10; // hidden; we draw our own
    }

    // ── Mouse interaction ──
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int tabMask = menu.getTabAvailability();
        int selectedTab = menu.getSelectedTab();

        // Check tab clicks
        for (int i = 0; i < TAB_COUNT; i++) {
            int tx = leftPos + 8 + i * (TAB_WIDTH + TAB_GAP);
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

        // Check module toggle clicks (armor only)
        if (selectedTab != DeviceHubMenu.TAB_RAILGUN) {
            List<String> moduleNames = menu.getModuleNameKeys();
            int moduleListY = topPos + MODULES_Y + 14;
            int maxVisible = (topPos + imageHeight - 30 - moduleListY) / MODULE_ROW_H;
            for (int i = 0; i < Math.min(moduleNames.size(), maxVisible); i++) {
                int idx = i + scrollOffset;
                if (idx >= moduleNames.size()) break;
                int rowY = moduleListY + i * MODULE_ROW_H - 1;
                int toggleX = leftPos + imageWidth - 48;
                if (mouseX >= toggleX && mouseX <= toggleX + TOGGLE_W && mouseY >= rowY && mouseY <= rowY + TOGGLE_H) {
                    PacketDistributor.sendToServer(new DeviceHubActionPacket(
                            DeviceHubActionPacket.ACTION_TOGGLE_MODULE, idx));
                    playClick();
                    return true;
                }
            }
        }

        // Check railgun setting toggles
        if (selectedTab == DeviceHubMenu.TAB_RAILGUN) {
            List<String> moduleNames = menu.getModuleNameKeys();
            int maxVisible = (topPos + imageHeight - 30 - (topPos + MODULES_Y + 14)) / MODULE_ROW_H;
            int toggleY = topPos + MODULES_Y + 14 + Math.min(moduleNames.size(), maxVisible) * MODULE_ROW_H + 8 + 14;
            int toggleX = leftPos + imageWidth - 48;

            if (mouseX >= toggleX && mouseX <= toggleX + TOGGLE_W) {
                if (mouseY >= toggleY && mouseY <= toggleY + TOGGLE_H) {
                    PacketDistributor.sendToServer(new DeviceHubActionPacket(DeviceHubActionPacket.ACTION_TOGGLE_TERRAIN, 0));
                    playClick();
                    return true;
                }
                toggleY += MODULE_ROW_H + 2;
                if (mouseY >= toggleY && mouseY <= toggleY + TOGGLE_H) {
                    PacketDistributor.sendToServer(new DeviceHubActionPacket(DeviceHubActionPacket.ACTION_TOGGLE_PVP, 0));
                    playClick();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Tab / Shift+Tab to switch tabs
        if (keyCode == 258) { // GLFW_KEY_TAB
            int dir = (modifiers & 1) != 0 ? -1 : 1; // shift = backward
            cycleTab(dir);
            return true;
        }
        // Left/Right arrow keys
        if (keyCode == 263) { // LEFT
            cycleTab(-1);
            return true;
        }
        if (keyCode == 262) { // RIGHT
            cycleTab(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0 && scrollOffset > 0) {
            scrollOffset--;
        } else if (scrollY < 0) {
            scrollOffset++;
        }
        return true;
    }

    // ── Drawing helpers ──

    private void renderTabBar(GuiGraphics gfx, int startX, int y, int selected, int tabMask) {
        for (int i = 0; i < TAB_COUNT; i++) {
            int tx = startX + i * (TAB_WIDTH + TAB_GAP);
            boolean available = (tabMask & (1 << i)) != 0;
            boolean active = i == selected;

            int borderColor = active ? TAB_CURRENT : (available ? BG_DEEP : TAB_DISABLED);
            int fillColor = active ? darken(TAB_CURRENT) : (available ? BG_LIGHT : darken(TAB_DISABLED));

            // Border
            gfx.fill(tx - 1, y - 1, tx + TAB_WIDTH + 1, y + TAB_HEIGHT + 1, borderColor);
            // Fill
            gfx.fill(tx, y, tx + TAB_WIDTH, y + TAB_HEIGHT, fillColor);

            // Label
            Component label = Component.translatable(TAB_LABEL_KEYS[i]);
            int textW = font.width(label);
            int textColor = active ? TEXT_PRIMARY : (available ? TEXT_SECONDARY : darken(TEXT_SECONDARY));
            gfx.drawString(font, label,
                    tx + (TAB_WIDTH - textW) / 2, y + 4, textColor, false);
        }
    }

    private void renderTabTooltips(GuiGraphics gfx, int mouseX, int mouseY, int startX, int y, int tabMask) {
        for (int i = 0; i < TAB_COUNT; i++) {
            int tx = startX + i * (TAB_WIDTH + TAB_GAP);
            boolean available = (tabMask & (1 << i)) != 0;
            if (!available && mouseX >= tx && mouseX <= tx + TAB_WIDTH && mouseY >= y && mouseY <= y + TAB_HEIGHT) {
                gfx.renderTooltip(font, Component.translatable(TAB_REQUIRED_KEYS[i]), mouseX, mouseY);
                return;
            }
        }
    }

    private void drawBar(GuiGraphics gfx, int x, int y, int w, int h, double ratio, int fillColor) {
        // Background
        gfx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF3C3C3C);
        gfx.fill(x, y, x + w, y + h, BG_DEEP);
        // Fill
        if (ratio > 0) {
            int filled = (int) (w * Math.min(1.0, ratio));
            if (filled > 0) {
                gfx.fill(x, y, x + filled, y + h, fillColor);
            }
        }
    }

    private void drawToggleButton(GuiGraphics gfx, int x, int y, boolean on) {
        int borderColor = on ? ENERGY_GREEN : TAB_DISABLED;
        int fillColor = on ? darken(ENERGY_GREEN) : 0xFF2A2A2A;
        gfx.fill(x - 1, y - 1, x + TOGGLE_W + 1, y + TOGGLE_H + 1, borderColor);
        gfx.fill(x, y, x + TOGGLE_W, y + TOGGLE_H, fillColor);
        String text = on ? "ON" : "OFF";
        int textColor = on ? TEXT_PRIMARY : TEXT_SECONDARY;
        int tw = font.width(text);
        gfx.drawString(font, Component.literal(text), x + (TOGGLE_W - tw) / 2, y + 2, textColor, false);
    }

    private static Component moduleName(String nameKey, int count) {
        Component name = Component.translatable(nameKey);
        if (count > 1) {
            return Component.translatable("ae2lt.device_hub.module.counted", name, count);
        }
        return Component.literal("  ").append(name);
    }

    private void drawSettingRow(GuiGraphics gfx, int x, int y, Component label, boolean on, int onColor) {
        gfx.drawString(font, Component.literal("  ").append(label), x, y + 1, TEXT_PRIMARY, false);
        int toggleX = leftPos + imageWidth - 48;
        int borderColor = on ? onColor : TAB_DISABLED;
        int fillColor = on ? darken(onColor) : 0xFF2A2A2A;
        gfx.fill(toggleX - 1, y - 1, toggleX + TOGGLE_W + 1, y + TOGGLE_H + 1, borderColor);
        gfx.fill(toggleX, y, toggleX + TOGGLE_W, y + TOGGLE_H, fillColor);
        String text = on ? "ON" : "OFF";
        int textColor = on ? TEXT_PRIMARY : TEXT_SECONDARY;
        int tw = font.width(text);
        gfx.drawString(font, Component.literal(text), toggleX + (TOGGLE_W - tw) / 2, y + 2, textColor, false);
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

    private static int darken(int argb) {
        int a = argb >>> 24;
        int r = (int) (((argb >> 16) & 0xFF) * 0.45);
        int g = (int) (((argb >> 8) & 0xFF) * 0.45);
        int b = (int) ((argb & 0xFF) * 0.45);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String formatEnergy(long value) {
        if (value >= 1_000_000_000) return String.format(Locale.ROOT, "%.1fG", value / 1_000_000_000.0);
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        return String.valueOf(value);
    }
}
