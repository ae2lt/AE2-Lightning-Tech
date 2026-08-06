package com.moakiee.ae2lt.client.hub;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.menu.hub.DeviceHubDisplayRules;
import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.network.hub.DeviceHubActionPacket;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.registry.ModItems;

public class DeviceHubScreen extends AbstractContainerScreen<DeviceHubMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/armor_settings_gui.png");
    private static final ResourceLocation CHECKBOX_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ae2", "textures/guis/checkbox.png");

    private static final int TEXTURE_SIZE = 256;
    private static final int CHECKBOX_TEXTURE_SIZE = 64;
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 223;

    private static final int TEXT_ON_LIGHT_BG = 0xFF6E748C;
    private static final int TEXT_ON_DARK_BG = 0xFFFFFFFF;
    private static final int ROW_HOVER = 0x304D4D67;
    private static final int ROW_SELECTED = 0x404D4D67;
    private static final int ROW_DIVIDER = 0xFF878DA4;
    private static final int BUTTON_BORDER = 0xFF4D5268;
    private static final int BUTTON_BORDER_HOVER = 0xFF8C96B3;
    private static final int BUTTON_DISABLED = 0xFF6B7086;
    private static final int BUTTON_FILL = 0xFF69708A;
    private static final int BUTTON_FILL_HOVER = 0xFF7B849F;
    private static final int BUTTON_FILL_DISABLED = 0xFF7D839B;
    private static final int BUTTON_TEXT = TEXT_ON_DARK_BG;

    private static final int TAB_COUNT = 5;
    private static final int TAB_Y = 0;
    private static final int TAB_WIDTH = 31;
    private static final int TAB_HEIGHT = 25;
    private static final int TAB_ICON_SIZE = 16;
    private static final int TAB_ACTIVE_SRC_Y = 225;
    private static final int TAB_ACTIVE_H = 26;
    private static final int[] TAB_X = {0, 31, 62, 93, 145};
    private static final int[] TAB_ACTIVE_SRC_X = {0, 31, 62, 93, 145};

    private static final int STATUS_X = 12;
    private static final int STATUS_Y = 36;
    private static final int STATUS_ICON_X = 13;
    private static final int STATUS_ICON_Y = 42;
    private static final int STATUS_TEXT_X = 34;
    private static final int STATUS_NAME_Y = 40;
    private static final int STATUS_LINE_Y = 53;
    private static final int STATUS_RIGHT = 166;

    private static final int MODULE_HEADER_X = 12;
    private static final int MODULE_HEADER_Y = 69;
    private static final int MODULE_LIST_X = 19;
    private static final int MODULE_LIST_Y = 83;
    private static final int MODULE_LIST_RIGHT = 166;
    private static final int MODULE_ROW_H = 14;
    private static final int MODULE_VISIBLE_ROWS = 4;
    private static final int MODULE_CHECKBOX_X = 142;

    private static final int SCROLL_X = 10;
    private static final int SCROLL_Y = 83;
    private static final int SCROLL_H = 56;
    private static final int SCROLL_SRC_X = 180;
    private static final int SCROLL_SRC_Y = 0;
    private static final int SCROLL_HOVER_SRC_Y = 17;
    private static final int SCROLL_SRC_W = 7;
    private static final int SCROLL_SRC_H = 15;
    private static final int SCROLL_HOVER_SRC_H = 14;

    private static final int CONFIG_X = 12;
    private static final int CONFIG_ROW_X = MODULE_LIST_X;
    private static final int CONFIG_HEADER_Y = 144;
    private static final int CONFIG_Y = 160;
    private static final int CONFIG_BUTTON_X = 124;
    private static final int CONFIG_BUTTON_W = 40;
    private static final int CONFIG_BUTTON_H = 12;
    private static final int CONFIG_ROW_H = 16;
    private static final int CONFIG_VISIBLE_ROWS = 3;
    private static final int CONFIG_SCROLL_Y = CONFIG_Y;
    private static final int CONFIG_SCROLL_H = 46;
    private static final int RAILGUN_SETTING_COUNT = 6;
    private static final int RAILGUN_SETTING_TERRAIN = 0;
    private static final int RAILGUN_SETTING_PVP = 1;
    private static final int RAILGUN_SETTING_SOUND = 2;
    private static final int RAILGUN_SETTING_CHAIN_DAMAGE = 3;
    private static final int RAILGUN_SETTING_CHARGED_SPLASH = 4;
    private static final int RAILGUN_SETTING_EXECUTION_MODE = 5;

    private static final int CHECKBOX_WIDTH = 22;
    private static final int CHECKBOX_HEIGHT = 12;
    private static final int CHECKBOX_OFF_SRC_Y = 28;
    private static final int CHECKBOX_ON_SRC_Y = 40;

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
    private int configScrollOffset = 0;
    private int lastConfigTab = -1;
    private int lastConfigModule = -1;

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
        resetConfigScrollWhenSelectionChanges(
                selectedTab,
                railgunTab ? -1 : menu.getSelectedModuleIndex());

        renderTabIcons(gfx);

        boolean hasDevice = (tabMask & (1 << selectedTab)) != 0;
        if (!hasDevice) {
            gfx.drawString(font, Component.translatable("ae2lt.device_hub.no_device"),
                    leftPos + STATUS_X, topPos + STATUS_Y + 9, TEXT_ON_DARK_BG, false);
            renderTabTooltips(gfx, mouseX, mouseY, tabMask);
            renderTooltip(gfx, mouseX, mouseY);
            return;
        }

        renderStatusPanel(gfx, railgunTab);
        renderModuleList(gfx, mouseX, mouseY, railgunTab);

        if (railgunTab) {
            renderRailgunSettings(gfx, mouseX, mouseY);
        } else {
            renderModuleConfig(gfx, mouseX, mouseY);
        }

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

    private void renderTabIcons(GuiGraphics gfx) {
        for (int i = 0; i < TAB_COUNT; i++) {
            int x = leftPos + TAB_X[i];
            ItemStack stack = tabDisplayStack(i);
            if (!stack.isEmpty()) {
                int iconX = x + (TAB_WIDTH - TAB_ICON_SIZE) / 2;
                gfx.renderItem(stack, iconX, topPos + 5);
            }
        }
    }

    private void renderStatusPanel(GuiGraphics gfx, boolean railgunTab) {
        int x = leftPos + STATUS_TEXT_X;
        ItemStack stack = selectedDeviceStack();
        if (!stack.isEmpty()) {
            gfx.renderItem(stack, leftPos + STATUS_ICON_X, topPos + STATUS_ICON_Y);
        }

        String deviceName = menu.getDeviceName();
        if (!deviceName.isEmpty()) {
            gfx.drawString(font, Component.literal(truncate(font, deviceName, STATUS_RIGHT - STATUS_TEXT_X)),
                    x, topPos + STATUS_NAME_Y, TEXT_ON_DARK_BG, false);
        }

        Component statusLine = Component.translatable("ae2lt.device_hub.status.line", statusText(railgunTab));
        gfx.drawString(font, statusLine, x, topPos + STATUS_LINE_Y, TEXT_ON_DARK_BG, false);
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

    private void renderModuleList(GuiGraphics gfx, int mouseX, int mouseY, boolean railgunTab) {
        List<String> moduleNameKeys = menu.getModuleNameKeys();
        List<Integer> moduleCounts = menu.getModuleCounts();
        List<Boolean> moduleEnabled = menu.getModuleEnabled();

        gfx.drawString(font, Component.translatable("ae2lt.device_hub.modules"),
                leftPos + MODULE_HEADER_X, topPos + MODULE_HEADER_Y, TEXT_ON_LIGHT_BG, false);

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
            if (hovered || idx == selectedModuleIndex) {
                gfx.fill(leftPos + MODULE_LIST_X - 1, rowY - 1,
                        leftPos + MODULE_LIST_RIGHT, rowY + MODULE_ROW_H - 1,
                        idx == selectedModuleIndex ? ROW_SELECTED : ROW_HOVER);
            }

            int count = idx < moduleCounts.size() ? moduleCounts.get(idx) : 1;
            int nameMaxWidth = railgunTab
                    ? MODULE_LIST_RIGHT - MODULE_LIST_X
                    : MODULE_CHECKBOX_X - MODULE_LIST_X - 6;
            String name = truncate(font, moduleName(moduleNameKeys.get(idx), count).getString(), nameMaxWidth);
            gfx.drawString(font, Component.literal(name), leftPos + MODULE_LIST_X, rowY + 2, TEXT_ON_DARK_BG, false);

            if (!railgunTab) {
                boolean enabled = idx < moduleEnabled.size() && moduleEnabled.get(idx);
                drawCheckbox(gfx, leftPos + MODULE_CHECKBOX_X, rowY + 1, enabled);
            }

            if (i < Math.min(moduleNameKeys.size(), MODULE_VISIBLE_ROWS) - 1) {
                gfx.fill(leftPos + MODULE_LIST_X,
                        rowY + MODULE_ROW_H - 1,
                        leftPos + MODULE_LIST_RIGHT,
                        rowY + MODULE_ROW_H,
                        ROW_DIVIDER);
            }
        }

        if (moduleNameKeys.size() > MODULE_VISIBLE_ROWS) {
            renderScrollBar(gfx, moduleNameKeys.size(), mouseX, mouseY);
        }
    }

    private void renderScrollBar(GuiGraphics gfx, int moduleCount, int mouseX, int mouseY) {
        renderScrollBar(
                gfx,
                moduleCount,
                MODULE_VISIBLE_ROWS,
                scrollOffset,
                SCROLL_X,
                SCROLL_Y,
                SCROLL_H,
                mouseX,
                mouseY);
    }

    private void renderModuleConfig(GuiGraphics gfx, int mouseX, int mouseY) {
        int x = leftPos + CONFIG_ROW_X;
        int y = topPos + CONFIG_Y;
        gfx.drawString(font, Component.translatable("ae2lt.celestweave.screen.module_options"),
                leftPos + CONFIG_X, topPos + CONFIG_HEADER_Y, TEXT_ON_LIGHT_BG, false);
        int count = moduleConfigCount();
        if (count <= 0) {
            return;
        }

        configScrollOffset = DeviceHubDisplayRules.clampScrollOffset(
                configScrollOffset, count, CONFIG_VISIBLE_ROWS);
        int rowY = y;
        for (int i = 0; i < Math.min(count, CONFIG_VISIBLE_ROWS); i++) {
            int configIndex = i + configScrollOffset;
            if (configIndex >= count) {
                break;
            }
            String value = menu.getModuleConfigValues().get(configIndex);
            boolean editable = menu.getModuleConfigEditable().get(configIndex);
            gfx.drawString(font, moduleConfigLabel(configIndex),
                    x, rowY + 1, TEXT_ON_DARK_BG, false);
            drawConfigValueButton(gfx, leftPos + CONFIG_BUTTON_X, rowY - 1, value, editable, mouseX, mouseY);
            rowY += CONFIG_ROW_H;
        }
        if (count > CONFIG_VISIBLE_ROWS) {
            renderConfigScrollBar(gfx, count, mouseX, mouseY);
        }
    }

    private void renderRailgunSettings(GuiGraphics gfx, int mouseX, int mouseY) {
        int x = leftPos + CONFIG_ROW_X;
        int y = topPos + CONFIG_Y;
        gfx.drawString(font, Component.translatable("ae2lt.device_hub.settings"),
                leftPos + CONFIG_X, topPos + CONFIG_HEADER_Y, TEXT_ON_LIGHT_BG, false);

        configScrollOffset = DeviceHubDisplayRules.clampScrollOffset(
                configScrollOffset, RAILGUN_SETTING_COUNT, CONFIG_VISIBLE_ROWS);
        int rowY = y;
        for (int i = 0; i < CONFIG_VISIBLE_ROWS; i++) {
            int settingIndex = i + configScrollOffset;
            if (settingIndex >= RAILGUN_SETTING_COUNT) {
                break;
            }
            Component label = railgunSettingLabel(settingIndex);
            if (settingIndex <= RAILGUN_SETTING_CHARGED_SPLASH) {
                drawSettingRow(gfx, x, rowY, label, railgunSettingEnabled(settingIndex));
            } else {
                gfx.drawString(font, label, x, rowY + 1, TEXT_ON_DARK_BG, false);
                drawConfigValueButton(
                        gfx,
                        leftPos + CONFIG_BUTTON_X,
                        rowY - 1,
                        railgunSettingValue(settingIndex),
                        true,
                        mouseX,
                        mouseY);
            }
            rowY += CONFIG_ROW_H;
        }
        renderConfigScrollBar(gfx, RAILGUN_SETTING_COUNT, mouseX, mouseY);
    }

    private Component railgunSettingLabel(int settingIndex) {
        return switch (settingIndex) {
            case RAILGUN_SETTING_TERRAIN -> Component.translatable("ae2lt.device_hub.setting.terrain");
            case RAILGUN_SETTING_PVP -> Component.translatable("ae2lt.device_hub.setting.pvp");
            case RAILGUN_SETTING_SOUND -> Component.translatable("ae2lt.device_hub.setting.sound");
            case RAILGUN_SETTING_CHAIN_DAMAGE -> Component.translatable("ae2lt.device_hub.setting.chain_damage");
            case RAILGUN_SETTING_CHARGED_SPLASH ->
                Component.translatable("ae2lt.railgun.config.charged_splash");
            case RAILGUN_SETTING_EXECUTION_MODE ->
                Component.translatable("ae2lt.railgun.config.overload_execution_mode");
            default -> Component.empty();
        };
    }

    private boolean railgunSettingEnabled(int settingIndex) {
        return switch (settingIndex) {
            case RAILGUN_SETTING_TERRAIN -> menu.isTerrainDestruction();
            case RAILGUN_SETTING_PVP -> menu.isPvp();
            case RAILGUN_SETTING_SOUND -> menu.isSoundEnabled();
            case RAILGUN_SETTING_CHAIN_DAMAGE -> menu.isChainDamage();
            case RAILGUN_SETTING_CHARGED_SPLASH -> menu.isChargedSplash();
            default -> false;
        };
    }

    private String railgunSettingValue(int settingIndex) {
        String key = switch (settingIndex) {
            case RAILGUN_SETTING_EXECUTION_MODE -> menu.isForceOverloadRemoval()
                    ? "ae2lt.railgun.config.overload_execution_mode.forced"
                    : "ae2lt.railgun.config.overload_execution_mode.normal";
            default -> "";
        };
        return key.isEmpty() ? "" : Component.translatable(key).getString();
    }

    private void drawSettingRow(GuiGraphics gfx, int x, int y, Component label, boolean on) {
        gfx.drawString(font, label, x, y + 1, TEXT_ON_DARK_BG, false);
        drawCheckbox(gfx, leftPos + MODULE_CHECKBOX_X, y, on);
    }

    private void drawCheckbox(GuiGraphics gfx, int x, int y, boolean checked) {
        gfx.blit(CHECKBOX_TEXTURE,
                x,
                y,
                0,
                checked ? CHECKBOX_ON_SRC_Y : CHECKBOX_OFF_SRC_Y,
                CHECKBOX_WIDTH,
                CHECKBOX_HEIGHT,
                CHECKBOX_TEXTURE_SIZE,
                CHECKBOX_TEXTURE_SIZE);
    }

    private void drawConfigValueButton(
            GuiGraphics gfx, int x, int y, String value, boolean editable, int mouseX, int mouseY) {
        boolean hovered = editable
                && mouseX >= x
                && mouseX <= x + CONFIG_BUTTON_W
                && mouseY >= y
                && mouseY <= y + CONFIG_BUTTON_H;
        int borderColor = editable ? hovered ? BUTTON_BORDER_HOVER : BUTTON_BORDER : BUTTON_DISABLED;
        int fillColor = editable ? hovered ? BUTTON_FILL_HOVER : BUTTON_FILL : BUTTON_FILL_DISABLED;
        gfx.fill(x - 1, y - 1, x + CONFIG_BUTTON_W + 1, y + CONFIG_BUTTON_H + 1, borderColor);
        gfx.fill(x, y, x + CONFIG_BUTTON_W, y + CONFIG_BUTTON_H, fillColor);
        String text = truncate(font, value, CONFIG_BUTTON_W - 4);
        int textColor = editable ? BUTTON_TEXT : TEXT_ON_DARK_BG;
        gfx.drawString(font, Component.literal(text),
                x + (CONFIG_BUTTON_W - font.width(text)) / 2, y + 2, textColor, false);
    }

    private boolean mouseClickedModuleConfig(double mouseX, double mouseY) {
        int count = moduleConfigCount();
        if (count <= 0) {
            return false;
        }
        int rowY = topPos + CONFIG_Y;
        int buttonX = leftPos + CONFIG_BUTTON_X;
        for (int i = 0; i < Math.min(count, CONFIG_VISIBLE_ROWS); i++) {
            int configIndex = i + configScrollOffset;
            if (configIndex >= count) {
                break;
            }
            boolean editable = menu.getModuleConfigEditable().get(configIndex);
            if (editable
                    && mouseX >= buttonX
                    && mouseX <= buttonX + CONFIG_BUTTON_W
                    && mouseY >= rowY - 1
                    && mouseY <= rowY - 1 + CONFIG_BUTTON_H) {
                PacketDistributor.sendToServer(new DeviceHubActionPacket(
                        DeviceHubActionPacket.ACTION_CYCLE_MODULE_CONFIG, configIndex));
                return true;
            }
            rowY += CONFIG_ROW_H;
        }
        return false;
    }

    private int moduleConfigCount() {
        return Math.min(
                Math.min(Math.min(menu.getModuleConfigKeys().size(), menu.getModuleConfigLabels().size()),
                        menu.getModuleConfigValues().size()),
                menu.getModuleConfigEditable().size());
    }

    private Component moduleConfigLabel(int index) {
        String key = menu.getModuleConfigKeys().get(index);
        if (key != null && !key.isBlank()) {
            return Component.translatable("ae2lt.celestweave.config." + key);
        }
        return Component.literal(menu.getModuleConfigLabels().get(index));
    }

    private static Component moduleName(String nameKey, int count) {
        Component name = Component.translatable(nameKey);
        if (count > 1) {
            return Component.translatable("ae2lt.device_hub.module.counted", name, count);
        }
        return name;
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

        boolean railgunTab = selectedTab == DeviceHubMenu.TAB_RAILGUN;
        if (!railgunTab) {
            if (mouseClickedModuleConfig(mouseX, mouseY)) {
                playClick();
                return true;
            }
            if (mouseClickedModule(mouseX, mouseY, false)) {
                playClick();
                return true;
            }
        } else {
            if (mouseClickedRailgunSettings(mouseX, mouseY)) {
                playClick();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean mouseClickedModule(double mouseX, double mouseY, boolean railgunTab) {
        List<String> moduleNames = menu.getModuleNameKeys();
        scrollOffset = DeviceHubDisplayRules.clampScrollOffset(
                scrollOffset, moduleNames.size(), MODULE_VISIBLE_ROWS);

        for (int i = 0; i < Math.min(moduleNames.size(), MODULE_VISIBLE_ROWS); i++) {
            int idx = i + scrollOffset;
            int rowY = topPos + MODULE_LIST_Y + i * MODULE_ROW_H;
            int checkboxX = leftPos + MODULE_CHECKBOX_X;
            if (!railgunTab
                    && mouseX >= checkboxX
                    && mouseX <= checkboxX + CHECKBOX_WIDTH
                    && mouseY >= rowY + 1
                    && mouseY <= rowY + 1 + CHECKBOX_HEIGHT) {
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
        int checkboxX = leftPos + MODULE_CHECKBOX_X;
        int buttonX = leftPos + CONFIG_BUTTON_X;
        int rowY = topPos + CONFIG_Y;
        for (int i = 0; i < CONFIG_VISIBLE_ROWS; i++) {
            int settingIndex = i + configScrollOffset;
            if (settingIndex >= RAILGUN_SETTING_COUNT) {
                break;
            }
            boolean checkboxSetting = settingIndex <= RAILGUN_SETTING_CHARGED_SPLASH;
            int controlX = checkboxSetting ? checkboxX : buttonX;
            int controlWidth = checkboxSetting ? CHECKBOX_WIDTH : CONFIG_BUTTON_W;
            if (mouseX >= controlX
                    && mouseX <= controlX + controlWidth
                    && mouseY >= rowY - (checkboxSetting ? 0 : 1)
                    && mouseY <= rowY - (checkboxSetting ? 0 : 1)
                            + (checkboxSetting ? CHECKBOX_HEIGHT : CONFIG_BUTTON_H)) {
                int action = switch (settingIndex) {
                    case RAILGUN_SETTING_TERRAIN -> DeviceHubActionPacket.ACTION_TOGGLE_TERRAIN;
                    case RAILGUN_SETTING_PVP -> DeviceHubActionPacket.ACTION_TOGGLE_PVP;
                    case RAILGUN_SETTING_SOUND -> DeviceHubActionPacket.ACTION_TOGGLE_SOUND;
                    case RAILGUN_SETTING_CHAIN_DAMAGE -> DeviceHubActionPacket.ACTION_TOGGLE_CHAIN_DAMAGE;
                    case RAILGUN_SETTING_CHARGED_SPLASH ->
                        DeviceHubActionPacket.ACTION_TOGGLE_CHARGED_SPLASH;
                    case RAILGUN_SETTING_EXECUTION_MODE ->
                        DeviceHubActionPacket.ACTION_TOGGLE_OVERLOAD_REMOVAL_MODE;
                    default -> -1;
                };
                if (action >= 0) {
                    PacketDistributor.sendToServer(new DeviceHubActionPacket(action, 0));
                    return true;
                }
            }
            rowY += CONFIG_ROW_H;
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
        if (mouseX >= leftPos + 8
                && mouseX <= leftPos + 168
                && mouseY >= topPos + 78
                && mouseY <= topPos + 142) {
            List<String> moduleNames = menu.getModuleNameKeys();
            scrollOffset = scroll(scrollOffset, scrollY);
            scrollOffset = DeviceHubDisplayRules.clampScrollOffset(
                    scrollOffset, moduleNames.size(), MODULE_VISIBLE_ROWS);
            return true;
        }

        int configCount = menu.getSelectedTab() == DeviceHubMenu.TAB_RAILGUN
                ? RAILGUN_SETTING_COUNT
                : moduleConfigCount();
        if (configCount > 0
                && mouseX >= leftPos + 8
                && mouseX <= leftPos + 175
                && mouseY >= topPos + CONFIG_HEADER_Y
                && mouseY <= topPos + GUI_HEIGHT) {
            configScrollOffset = scroll(configScrollOffset, scrollY);
            configScrollOffset = DeviceHubDisplayRules.clampScrollOffset(
                    configScrollOffset, configCount, CONFIG_VISIBLE_ROWS);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void renderConfigScrollBar(GuiGraphics gfx, int configCount, int mouseX, int mouseY) {
        renderScrollBar(
                gfx,
                configCount,
                CONFIG_VISIBLE_ROWS,
                configScrollOffset,
                SCROLL_X,
                CONFIG_SCROLL_Y,
                CONFIG_SCROLL_H,
                mouseX,
                mouseY);
    }

    private void renderScrollBar(
            GuiGraphics gfx,
            int itemCount,
            int visibleRows,
            int offset,
            int relativeX,
            int relativeY,
            int height,
            int mouseX,
            int mouseY) {
        int thumbRange = height - SCROLL_SRC_H;
        int thumbY = topPos + relativeY
                + thumbRange * offset / Math.max(1, itemCount - visibleRows);
        int thumbX = leftPos + relativeX;
        boolean hovered = mouseX >= thumbX
                && mouseX < thumbX + SCROLL_SRC_W
                && mouseY >= thumbY
                && mouseY < thumbY + SCROLL_SRC_H;
        gfx.blit(
                TEXTURE,
                thumbX,
                thumbY,
                SCROLL_SRC_X,
                hovered ? SCROLL_HOVER_SRC_Y : SCROLL_SRC_Y,
                SCROLL_SRC_W,
                hovered ? SCROLL_HOVER_SRC_H : SCROLL_SRC_H,
                TEXTURE_SIZE,
                TEXTURE_SIZE);
    }

    private void resetConfigScrollWhenSelectionChanges(int selectedTab, int selectedModule) {
        if (selectedTab == lastConfigTab && selectedModule == lastConfigModule) {
            return;
        }
        lastConfigTab = selectedTab;
        lastConfigModule = selectedModule;
        configScrollOffset = 0;
    }

    private static int scroll(int offset, double scrollY) {
        if (scrollY > 0 && offset > 0) {
            return offset - 1;
        }
        if (scrollY < 0) {
            return offset + 1;
        }
        return offset;
    }

    private void renderTabTooltips(GuiGraphics gfx, int mouseX, int mouseY, int tabMask) {
        for (int i = 0; i < TAB_COUNT; i++) {
            int tx = leftPos + TAB_X[i];
            boolean available = (tabMask & (1 << i)) != 0;
            if (mouseX >= tx
                    && mouseX <= tx + TAB_WIDTH
                    && mouseY >= topPos + TAB_Y
                    && mouseY <= topPos + TAB_Y + TAB_HEIGHT) {
                if (available) {
                    ItemStack stack = tabStack(i);
                    if (!stack.isEmpty()) {
                        gfx.renderTooltip(font, stack, mouseX, mouseY);
                    } else {
                        gfx.renderTooltip(font, Component.translatable(TAB_LABEL_KEYS[i]), mouseX, mouseY);
                    }
                } else {
                    gfx.renderTooltip(font, Component.translatable(TAB_REQUIRED_KEYS[i]), mouseX, mouseY);
                }
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

    private ItemStack selectedDeviceStack() {
        return tabStack(menu.getSelectedTab());
    }

    private ItemStack tabDisplayStack(int tab) {
        ItemStack equipped = tabStack(tab);
        return equipped.isEmpty() ? defaultTabStack(tab) : equipped;
    }

    private static ItemStack defaultTabStack(int tab) {
        return switch (tab) {
            case DeviceHubMenu.TAB_HELMET -> new ItemStack(ModItems.CELESTWEAVE_OCULUS.get());
            case DeviceHubMenu.TAB_CHESTPLATE -> new ItemStack(ModItems.CELESTWEAVE_CORE.get());
            case DeviceHubMenu.TAB_LEGGINGS -> new ItemStack(ModItems.CELESTWEAVE_CONDUIT.get());
            case DeviceHubMenu.TAB_BOOTS -> new ItemStack(ModItems.CELESTWEAVE_STRIDE.get());
            case DeviceHubMenu.TAB_RAILGUN -> new ItemStack(ModItems.ELECTROMAGNETIC_RAILGUN.get());
            default -> ItemStack.EMPTY;
        };
    }

    private ItemStack tabStack(int tab) {
        if (minecraft == null || minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        Player player = minecraft.player;
        return switch (tab) {
            case DeviceHubMenu.TAB_HELMET -> armorStack(player, EquipmentSlot.HEAD);
            case DeviceHubMenu.TAB_CHESTPLATE -> armorStack(player, EquipmentSlot.CHEST);
            case DeviceHubMenu.TAB_LEGGINGS -> armorStack(player, EquipmentSlot.LEGS);
            case DeviceHubMenu.TAB_BOOTS -> armorStack(player, EquipmentSlot.FEET);
            case DeviceHubMenu.TAB_RAILGUN -> railgunStack(player);
            default -> ItemStack.EMPTY;
        };
    }

    private static ItemStack armorStack(Player player, EquipmentSlot slot) {
        ItemStack stack = player.getItemBySlot(slot);
        return stack.getItem() instanceof BaseCelestweaveArmorItem ? stack : ItemStack.EMPTY;
    }

    private static ItemStack railgunStack(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof ElectromagneticRailgunItem) {
            return main;
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.getItem() instanceof ElectromagneticRailgunItem ? offhand : ItemStack.EMPTY;
    }
}
