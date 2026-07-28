package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;

import com.moakiee.ae2lt.api.client.PatternProviderToolbarButtonHider;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.mixin.client.AEBaseScreenAccessor;
import com.moakiee.ae2lt.mixin.client.PatternProviderScreenAccessor;
import com.moakiee.ae2lt.mixin.client.VerticalButtonBarAccessor;

public class OverloadedPatternProviderScreen<M extends OverloadedPatternProviderMenu> extends PatternProviderScreen<M> {

    private static final List<Component> RETURN_TIP_OFF =
            List.of(Component.translatable("ae2lt.gui.return_mode.off"));
    private static final List<Component> RETURN_TIP_AUTO =
            List.of(Component.translatable("ae2lt.gui.return_mode.auto"));
    private static final List<Component> RETURN_TIP_EJECT =
            List.of(Component.translatable("ae2lt.gui.return_mode.eject"));
    private static final List<Component> FILTER_TIP_ON =
            List.of(Component.translatable("ae2lt.gui.filtered_import.on"));
    private static final List<Component> FILTER_TIP_OFF =
            List.of(Component.translatable("ae2lt.gui.filtered_import.off"));
    private static final List<Component> STRATEGY_TIP_SINGLE =
            List.of(Component.translatable("ae2lt.gui.wireless_strategy.single"));
    private static final List<Component> STRATEGY_TIP_EVEN =
            List.of(Component.translatable("ae2lt.gui.wireless_strategy.even"));
    private static final List<Component> SPEED_TIP_FAST =
            List.of(Component.translatable("ae2lt.gui.wireless_speed.fast"));
    private static final List<Component> SPEED_TIP_NORMAL =
            List.of(Component.translatable("ae2lt.gui.wireless_speed.normal"));

    private final TextureToggleButton modeButton;
    private final TextureToggleButton autoReturnButton;
    private final TextureToggleButton wirelessStrategyButton;
    private final TextureToggleButton wirelessSpeedButton;
    private final TextureToggleButton filteredImportButton;
    private final ProviderBlockingModeButton blockingModeButton;

    private static final int SLOTS_PER_PAGE = 36;

    public OverloadedPatternProviderScreen(M menu, Inventory playerInventory,
                                           Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        removeHiddenToolbarButtons();
        removeVanillaBlockingModeButton();

        this.blockingModeButton = new ProviderBlockingModeButton(
                btn -> menu.clientCycleBlockingMode());
        addToLeftToolbar(this.blockingModeButton);

        addToLeftToolbar(FrequencyBindingClient.createToolbarButton(menu));

        this.autoReturnButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.AUTO_RETURN,
                btn -> menu.clientToggleAutoReturn());
        addToLeftToolbar(this.autoReturnButton);

        this.modeButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.MODE,
                btn -> menu.clientToggleMode());
        this.modeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.provider_mode.wireless")));
        this.modeButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.provider_mode.normal")));
        addToLeftToolbar(this.modeButton);

        this.wirelessStrategyButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.WIRELESS_STRATEGY,
                btn -> menu.clientToggleWirelessDispatchMode());
        this.wirelessStrategyButton.setTooltipOn(STRATEGY_TIP_EVEN);
        this.wirelessStrategyButton.setTooltipOff(STRATEGY_TIP_SINGLE);
        addToLeftToolbar(this.wirelessStrategyButton);

        this.wirelessSpeedButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.SPEED,
                btn -> menu.clientToggleWirelessSpeedMode());
        this.wirelessSpeedButton.setTooltipOn(SPEED_TIP_FAST);
        this.wirelessSpeedButton.setTooltipOff(SPEED_TIP_NORMAL);
        addToLeftToolbar(this.wirelessSpeedButton);

        this.filteredImportButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.FILTERED_IMPORT,
                btn -> menu.clientToggleFilteredImport());
        this.filteredImportButton.setTooltipOn(FILTER_TIP_ON);
        this.filteredImportButton.setTooltipOff(FILTER_TIP_OFF);
        addToLeftToolbar(this.filteredImportButton);
    }

    @Override
    protected void init() {
        super.init();

        alignSlotPositions();
    }

    /**
     * After AE2's layout system positions all ENCODED_PATTERN slots,
     * copy page-0 positions to every subsequent page so all pages
     * share the same screen coordinates. Only active/inactive toggles
     * are needed to switch pages — no per-frame coordinate remapping.
     */
    private void alignSlotPositions() {
        var patternSlots = this.menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        int total = patternSlots.size();
        if (total <= SLOTS_PER_PAGE) return;

        for (int i = SLOTS_PER_PAGE; i < total; i++) {
            int ref = i % SLOTS_PER_PAGE;
            patternSlots.get(i).x = patternSlots.get(ref).x;
            patternSlots.get(i).y = patternSlots.get(ref).y;
        }
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);
        int tp = this.menu.getTotalPages();
        if (tp > 1) {
            String pageText = (this.menu.getCurrentPage() + 1) + "/" + tp;
            int textWidth = this.font.width(pageText);
            guiGraphics.drawString(this.font, pageText,
                    PatternProviderPageIndicator.centeredX(this.imageWidth, textWidth),
                    33,
                    0x404040,
                    false);
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.menu.showPage(this.menu.getCurrentPage());

        setTextContent("dialog_title", Component.translatable(this.menu.getTitleTranslationKey()));
        this.blockingModeButton.setState(this.menu.getBlockingModeOrdinal());
        this.blockingModeButton.setVisibility(this.menu.isBlockingModeVisible());

        this.modeButton.setState(this.menu.isWirelessMode());
        this.modeButton.setVisibility(this.menu.isModeSwitchVisible());

        this.autoReturnButton.setTooltipAt(ReturnMode.OFF.ordinal(), RETURN_TIP_OFF);
        this.autoReturnButton.setTooltipAt(ReturnMode.AUTO.ordinal(), RETURN_TIP_AUTO);
        this.autoReturnButton.setTooltipAt(ReturnMode.EJECT.ordinal(), RETURN_TIP_EJECT);
        this.autoReturnButton.setStateIndex(this.menu.getReturnModeOrdinal());

        this.filteredImportButton.setState(this.menu.isFilteredImport());
        this.filteredImportButton.setVisibility(this.menu.isFilteredImportVisible());

        this.wirelessStrategyButton.setState(this.menu.isEvenDistributionMode());
        this.wirelessStrategyButton.setVisibility(
                this.menu.isWirelessMode() && this.menu.isWirelessTuningVisible());

        this.wirelessSpeedButton.setState(this.menu.isFastSpeedMode());
        this.wirelessSpeedButton.setVisibility(
                this.menu.isWirelessMode() && this.menu.isWirelessTuningVisible());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.menu.getTotalPages() > 1) {
            var direction = PatternProviderPageScroll.directionForDelta(scrollY);
            if (direction == PatternProviderPageScroll.Direction.PREVIOUS) {
                this.menu.clientPrevPage();
                return true;
            }
            if (direction == PatternProviderPageScroll.Direction.NEXT) {
                this.menu.clientNextPage();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void removeVanillaBlockingModeButton() {
        var toolbar = ((AEBaseScreenAccessor) this).ae2lt$getVerticalToolbar();
        var buttons = ((VerticalButtonBarAccessor) toolbar).ae2lt$getButtons();
        buttons.remove(((PatternProviderScreenAccessor) this).ae2lt$getBlockingModeButton());
    }

    private void removeHiddenToolbarButtons() {
        var toolbar = ((AEBaseScreenAccessor) this).ae2lt$getVerticalToolbar();
        var buttons = ((VerticalButtonBarAccessor) toolbar).ae2lt$getButtons();
        PatternProviderToolbarButtonHider.removeHiddenToolbarButtons(buttons);
    }

}
