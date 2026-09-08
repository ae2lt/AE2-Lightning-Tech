package com.moakiee.ae2lt.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.TabButton;
import appeng.client.gui.widgets.AETextField;
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
    private final AETextField machineParallelism;
    private final Button applyParallelism;
    private int lastSyncedParallelism;

    public OverloadedPatternProviderAdvancedScreen(OverloadedPatternProviderScreen<M> parent) {
        super(parent, "/screens/overloaded_pattern_provider_advanced.json");

        var backLabel = Component.translatable(parent.getMenu().getTitleTranslationKey());
        widgets.add("return", new TabButton(Icon.ARROW_LEFT, backLabel, btn -> returnToParent()));

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

        machineParallelism = widgets.addTextField("machineParallelism");
        machineParallelism.setMaxLength(10);
        machineParallelism.setFilter(OverloadedPatternProviderAdvancedScreen::isParallelismDraft);
        lastSyncedParallelism = menu.machineParallelism;
        machineParallelism.setValue(Integer.toString(lastSyncedParallelism));
        machineParallelism.setTooltip(Tooltip.create(Component.translatable(
                "ae2lt.gui.provider_advanced.machine_parallelism_tooltip")));
        applyParallelism = widgets.addButton("applyParallelism",
                Component.translatable("gui.done"), this::applyParallelism);
    }

    private static boolean isParallelismDraft(String text) {
        if (text.isEmpty()) return true;
        try {
            return text.chars().allMatch(c -> c >= '0' && c <= '9')
                    && Integer.parseInt(text) >= 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private int draftedParallelism() {
        try {
            return Integer.parseInt(machineParallelism.getValue());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void applyParallelism() {
        int value = draftedParallelism();
        if (value > 0) {
            menu.clientSetMachineParallelism(value);
        }
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

        if (lastSyncedParallelism != menu.machineParallelism) {
            if (!machineParallelism.isFocused()
                    || draftedParallelism() == lastSyncedParallelism) {
                machineParallelism.setValue(Integer.toString(menu.machineParallelism));
            }
            lastSyncedParallelism = menu.machineParallelism;
        }
        machineParallelism.setVisible(wirelessTuningVisible);
        machineParallelism.setEditable(wirelessTuningActive);
        applyParallelism.visible = wirelessTuningVisible;
        applyParallelism.active = wirelessTuningActive
                && draftedParallelism() > 0
                && draftedParallelism() != menu.machineParallelism;
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
        if (wirelessTuningVisible) {
            guiGraphics.drawString(font,
                    Component.translatable("ae2lt.gui.provider_advanced.machine_parallelism"),
                    14, 105, wirelessColor, false);
            guiGraphics.drawString(font,
                    Component.translatable("ae2lt.gui.provider_advanced.parallelism_hint"),
                    14, 147, 0x707070, false);
        }
        if (wirelessTuningVisible && !menu.isWirelessMode()) {
            guiGraphics.drawString(
                    font,
                    Component.translatable("ae2lt.gui.provider_advanced.wireless_hint"),
                    14,
                    161,
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
        if (machineParallelism.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            if (applyParallelism.active) applyParallelism();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || !machineParallelism.isFocused()
                        && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            returnToParent();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
