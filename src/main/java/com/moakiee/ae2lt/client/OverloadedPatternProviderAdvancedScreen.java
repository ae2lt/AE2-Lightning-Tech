package com.moakiee.ae2lt.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.SlotSemantics;

import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;

public final class OverloadedPatternProviderAdvancedScreen<M extends OverloadedPatternProviderMenu>
        extends AESubScreen<M, OverloadedPatternProviderScreen<M>> {
    private static final int LABEL_X = 38;
    private static final int STRATEGY_Y = 33;
    private static final int SPEED_Y = 57;
    private static final int FILTER_Y = 81;

    private final TextureToggleButton wirelessStrategyButton;
    private final TextureToggleButton wirelessSpeedButton;
    private final TextureToggleButton filteredImportButton;

    public OverloadedPatternProviderAdvancedScreen(OverloadedPatternProviderScreen<M> parent) {
        super(parent, "/screens/overloaded_pattern_provider_advanced.json");

        var backLabel = Component.translatable(parent.getMenu().getTitleTranslationKey());
        widgets.add("return", new TabButton(Icon.BACK, backLabel, btn -> returnToParent()));

        this.wirelessStrategyButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.WIRELESS_STRATEGY,
                state -> menu.clientToggleWirelessDispatchMode());
        this.wirelessStrategyButton.setTooltipOn(
                List.of(Component.translatable("ae2lt.gui.wireless_strategy.even")));
        this.wirelessStrategyButton.setTooltipOff(
                List.of(Component.translatable("ae2lt.gui.wireless_strategy.single")));
        widgets.add("wirelessStrategy", this.wirelessStrategyButton);

        this.wirelessSpeedButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.SPEED,
                state -> menu.clientToggleWirelessSpeedMode());
        this.wirelessSpeedButton.setTooltipOn(
                List.of(Component.translatable("ae2lt.gui.wireless_speed.fast")));
        this.wirelessSpeedButton.setTooltipOff(
                List.of(Component.translatable("ae2lt.gui.wireless_speed.normal")));
        widgets.add("wirelessSpeed", this.wirelessSpeedButton);

        this.filteredImportButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.FILTERED_IMPORT,
                state -> menu.clientToggleFilteredImport());
        this.filteredImportButton.setTooltipOn(
                List.of(Component.translatable("ae2lt.gui.filtered_import.on")));
        this.filteredImportButton.setTooltipOff(
                List.of(Component.translatable("ae2lt.gui.filtered_import.off")));
        widgets.add("filteredImport", this.filteredImportButton);
    }

    @Override
    protected boolean shouldAddToolbar() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        setSlotsHidden(SlotSemantics.TOOLBOX, true);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        boolean wirelessTuningVisible = menu.isWirelessTuningVisible();
        boolean wirelessTuningActive = wirelessTuningVisible && menu.isWirelessMode();

        this.wirelessStrategyButton.setState(menu.isEvenDistributionMode());
        this.wirelessStrategyButton.setVisibility(wirelessTuningVisible);
        this.wirelessStrategyButton.active = wirelessTuningActive;

        this.wirelessSpeedButton.setState(menu.isFastSpeedMode());
        this.wirelessSpeedButton.setVisibility(wirelessTuningVisible);
        this.wirelessSpeedButton.active = wirelessTuningActive;

        this.filteredImportButton.setState(menu.isFilteredImport());
        this.filteredImportButton.setVisibility(menu.isFilteredImportVisible());
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        boolean wirelessTuningVisible = menu.isWirelessTuningVisible();
        int wirelessColor = menu.isWirelessMode() ? 0x404040 : 0x909090;
        if (wirelessTuningVisible) {
            guiGraphics.drawString(
                    font,
                    Component.translatable("ae2lt.gui.provider_advanced.distribution"),
                    LABEL_X,
                    STRATEGY_Y,
                    wirelessColor,
                    false);
            guiGraphics.drawString(
                    font,
                    Component.translatable("ae2lt.gui.provider_advanced.probe"),
                    LABEL_X,
                    SPEED_Y,
                    wirelessColor,
                    false);
        }
        if (menu.isFilteredImportVisible()) {
            guiGraphics.drawString(
                    font,
                    Component.translatable("ae2lt.gui.provider_advanced.input_filter"),
                    LABEL_X,
                    FILTER_Y,
                    0x404040,
                    false);
        }
        if (wirelessTuningVisible && !menu.isWirelessMode()) {
            guiGraphics.drawString(
                    font,
                    Component.translatable("ae2lt.gui.provider_advanced.wireless_hint"),
                    14,
                    105,
                    0x707070,
                    false);
        }
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            returnToParent();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
