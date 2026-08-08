package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.config.ActionItems;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.menu.SlotSemantics;

import com.moakiee.ae2lt.api.client.PatternProviderToolbarButtonHider;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.mixin.client.AEBaseScreenAccessor;
import com.moakiee.ae2lt.mixin.client.PatternProviderScreenAccessor;
import com.moakiee.ae2lt.mixin.client.VerticalButtonBarAccessor;
import com.moakiee.ae2lt.util.SlotPositionAccess;

public class OverloadedPatternProviderScreen<M extends OverloadedPatternProviderMenu> extends PatternProviderScreen<M> {

    private static final List<Component> RETURN_TIP_OFF =
            List.of(Component.translatable("ae2lt.gui.return_mode.off"));
    private static final List<Component> RETURN_TIP_AUTO =
            List.of(Component.translatable("ae2lt.gui.return_mode.auto"));
    private static final List<Component> RETURN_TIP_EJECT =
            List.of(Component.translatable("ae2lt.gui.return_mode.eject"));
    private static final List<Component> ADAPTIVE_BATCH_TIP_ON =
            List.of(Component.translatable("ae2lt.gui.adaptive_batch.on"));
    private static final List<Component> ADAPTIVE_BATCH_TIP_OFF =
            List.of(Component.translatable("ae2lt.gui.adaptive_batch.off"));

    private final TextureToggleButton modeButton;
    private final TextureToggleButton autoReturnButton;
    private final ProviderBlockingModeButton blockingModeButton;
    private final TextureToggleButton adaptiveBatchButton;
    private final ActionButton advancedSettingsButton;

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

        this.adaptiveBatchButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.ADAPTIVE_BATCH,
                state -> menu.clientToggleAdaptiveBatch());
        this.adaptiveBatchButton.setTooltipOn(ADAPTIVE_BATCH_TIP_ON);
        this.adaptiveBatchButton.setTooltipOff(ADAPTIVE_BATCH_TIP_OFF);
        addToLeftToolbar(this.adaptiveBatchButton);

        this.advancedSettingsButton = new ActionButton(
                ActionItems.TERMINAL_SETTINGS,
                () -> switchToScreen(new OverloadedPatternProviderAdvancedScreen<>(this)));
        this.advancedSettingsButton.setMessage(
                Component.translatable("ae2lt.gui.provider_advanced.open"));
        addToLeftToolbar(this.advancedSettingsButton);
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
            SlotPositionAccess.set(patternSlots.get(i), patternSlots.get(ref).x, patternSlots.get(ref).y);
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

        this.adaptiveBatchButton.setState(this.menu.isAdaptiveBatchEnabled());
        this.advancedSettingsButton.setVisibility(
                this.menu.isWirelessTuningVisible() || this.menu.isFilteredImportVisible());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.menu.getTotalPages() > 1) {
            var direction = PatternProviderPageScroll.directionForDelta(delta);
            if (direction == PatternProviderPageScroll.Direction.PREVIOUS) {
                this.menu.clientPrevPage();
                return true;
            }
            if (direction == PatternProviderPageScroll.Direction.NEXT) {
                this.menu.clientNextPage();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
