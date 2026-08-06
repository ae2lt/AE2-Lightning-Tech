package com.moakiee.ae2lt.client;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.client.gui.GuiTextLayout;
import com.moakiee.ae2lt.menu.OverloadedInterfaceMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.client.gui.Icon;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import appeng.api.upgrades.Upgrades;
import appeng.menu.SlotSemantics;


public class OverloadedInterfaceScreen extends AEBaseScreen<OverloadedInterfaceMenu> {

    private static final int SLOTS_PER_PAGE = 18;
    private static final int COLS = 9;
    private static final int SLOT_SPACING = 18;
    private static final int AMT_BTN_SIZE = 16;
    private static final int AMT_ROW1_Y = 35;
    private static final int AMT_ROW2_Y = 95;
    private static final int AMT_START_X = 8;
    private static final int PAGE_INDICATOR_Y = 18;

    private final SettingToggleButton<FuzzyMode> fuzzyMode;
    private final TextureToggleButton modeButton;
    private final TextureToggleButton exportModeButton;
    private final TextureToggleButton importModeButton;
    private final TextureToggleButton speedButton;
    private final PageButton prevPageButton;
    private final PageButton nextPageButton;
    private final List<SetAmountButton> amountButtons = new ArrayList<>();
    private final List<Slot> configSlots;

    private int lastKnownPage = -1;

    public OverloadedInterfaceScreen(OverloadedInterfaceMenu menu, Inventory playerInventory,
                                     Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        var filterSlot = menu.getFilterSlot();
        var upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE).stream()
                .filter(slot -> slot != filterSlot)
                .collect(Collectors.toList());
        widgets.add("upgrades", new UpgradesPanel(upgradeSlots, this::getCompatibleUpgrades));
        if (filterSlot != null) {
            widgets.add("overloadedFilter", new UpgradesPanel(List.of(filterSlot), List::of));
        }
        if (menu.getToolbox().isPresent()) {
            this.widgets.add("toolbox", new ToolboxPanel(style, menu.getToolbox().getName()));
        }

        addToLeftToolbar(FrequencyBindingClient.createToolbarButton(menu));

        this.fuzzyMode = new ServerSettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        addToLeftToolbar(this.fuzzyMode);

        this.nextPageButton = new PageButton(Icon.ARROW_RIGHT, btn -> menu.nextPage());
        this.nextPageButton.setMessage(Component.translatable("ae2lt.gui.overloaded_interface.next_page"));
        addToLeftToolbar(this.nextPageButton);

        this.prevPageButton = new PageButton(Icon.ARROW_LEFT, btn -> menu.prevPage());
        this.prevPageButton.setMessage(Component.translatable("ae2lt.gui.overloaded_interface.prev_page"));
        addToLeftToolbar(this.prevPageButton);

        this.modeButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.MODE, btn -> menu.cycleInterfaceMode());
        this.modeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.interface_mode.wireless")));
        this.modeButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.interface_mode.normal")));
        addToLeftToolbar(this.modeButton);

        this.exportModeButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.AUTO_EXPORT, btn -> menu.cycleExportMode());
        this.exportModeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.export_mode.auto")));
        this.exportModeButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.export_mode.off")));
        addToLeftToolbar(this.exportModeButton);

        this.importModeButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.AUTO_IMPORT, btn -> menu.cycleImportMode());
        addToLeftToolbar(this.importModeButton);

        this.speedButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.SPEED, btn -> menu.cycleIOSpeed());
        this.speedButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.io_speed.fast")));
        this.speedButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.io_speed.normal")));
        addToLeftToolbar(this.speedButton);

        widgets.addOpenPriorityButton();

        this.configSlots = menu.getAllConfigSlots();
        for (int i = 0; i < configSlots.size(); i++) {
            final int slotIdx = i;
            var button = new SetAmountButton(btn -> {
                if (hasShiftDown()) {
                    menu.toggleUnlimited(configSlots.get(slotIdx).getContainerSlot());
                } else {
                    menu.openSetAmountMenu(configSlots.get(slotIdx).getContainerSlot());
                }
            });
            button.setDisableBackground(true);
            button.setMessage(Component.translatable("ae2lt.gui.set_amount.message"));
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("ae2lt.gui.set_amount.tooltip")));
            amountButtons.add(button);
        }
    }

    @Override
    protected void init() {
        super.init();

        for (var btn : amountButtons) {
            addRenderableWidget(btn);
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.fuzzyMode.set(menu.getFuzzyMode());
        this.fuzzyMode.setVisibility(menu.hasUpgrade(AEItems.FUZZY_CARD));

        this.modeButton.setState(menu.interfaceMode == 1);
        this.exportModeButton.setState(menu.exportMode == OverloadedInterfaceBlockEntity.ExportMode.AUTO.ordinal());
        this.speedButton.setState(menu.ioSpeedMode == 1);

        var impModes = OverloadedInterfaceBlockEntity.ImportMode.values();
        int importModeIndex = Math.max(0, Math.min(menu.importMode, impModes.length - 1));
        importModeButton.setTooltipAt(impModes[0].ordinal(),
                List.of(Component.translatable("ae2lt.gui.import_mode.off")));
        importModeButton.setTooltipAt(impModes[1].ordinal(),
                List.of(Component.translatable("ae2lt.gui.import_mode.auto")));
        importModeButton.setTooltipAt(impModes[2].ordinal(),
                List.of(Component.translatable("ae2lt.gui.import_mode.eject")));
        importModeButton.setStateIndex(importModeIndex);

        if (menu.currentPage != lastKnownPage) {
            lastKnownPage = menu.currentPage;
            menu.showPage(menu.currentPage);
        }

        int page = menu.currentPage;
        int start = page * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, configSlots.size());

        for (int i = 0; i < amountButtons.size(); i++) {
            var button = amountButtons.get(i);
            if (i >= start && i < end) {
                int inPage = i - start;
                int col = inPage % COLS;
                int row = inPage / COLS;
                button.setPosition(
                        this.leftPos + AMT_START_X + col * SLOT_SPACING,
                        this.topPos + (row == 0 ? AMT_ROW1_Y : AMT_ROW2_Y));
                var item = configSlots.get(i).getItem();
                button.visible = !item.isEmpty();
            } else {
                button.visible = false;
            }
        }

        boolean hasMultiplePages = menu.totalPages > 1;
        prevPageButton.setVisibility(hasMultiplePages && page > 0);
        nextPageButton.setVisibility(hasMultiplePages && page < menu.totalPages - 1);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY,
                        int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        String pageText = (menu.currentPage + 1) + "/" + menu.totalPages;
        int textWidth = this.font.width(pageText);
        guiGraphics.drawString(this.font, pageText,
                GuiTextLayout.centeredX(this.imageWidth, textWidth), PAGE_INDICATOR_Y,
                style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB(), false);

        int page = menu.currentPage;
        int start = page * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, configSlots.size());
        for (int i = start; i < end; i++) {
            if (menu.isSlotUnlimited(i)) {
                var slot = configSlots.get(i);
                if (!slot.getItem().isEmpty()) {
                    guiGraphics.drawString(this.font, "\u221E",
                            slot.x + 10, slot.y - 10, 0xFF00FF00, true);
                }
            }
        }
    }

    private List<Component> getCompatibleUpgrades() {
        var list = new ArrayList<Component>();
        list.add(GuiText.CompatibleUpgrades.text());
        list.addAll(Upgrades.getTooltipLinesForMachine(menu.getUpgrades().getUpgradableItem()));
        return list;
    }


    static class SetAmountButton extends appeng.client.gui.widgets.IconButton {
        public SetAmountButton(OnPress onPress) {
            super(onPress);
        }

        @Override
        protected Icon getIcon() {
            return isHoveredOrFocused() ? Icon.COG : Icon.COG_DISABLED;
        }
    }

    static class PageButton extends IconButton {
        private final Icon icon;

        public PageButton(Icon icon, OnPress onPress) {
            super(onPress);
            this.icon = icon;
        }

        @Override
        protected Icon getIcon() {
            return this.icon;
        }
    }
}
