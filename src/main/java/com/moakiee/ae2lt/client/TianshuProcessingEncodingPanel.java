/*
 * Derived from Applied Energistics 2's ProcessingEncodingPanel.
 * Copyright (c) Applied Energistics 2 contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

package com.moakiee.ae2lt.client;

import appeng.api.config.ActionItems;
import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

final class TianshuProcessingEncodingPanel extends TianshuEncodingModePanel {
    private static final Blitter BG = Blitter.texture("guis/pattern_modes.png").src(0, 70, 124, 66);

    private final ActionButton clearButton;
    private final ActionButton cycleOutputButton;
    private final Scrollbar scrollbar;

    TianshuProcessingEncodingPanel(
            TianshuPatternEncodingTermScreen<?> screen,
            WidgetContainer widgets) {
        super(screen, widgets);

        clearButton = new ActionButton(ActionItems.S_CLOSE, action -> menu.clear());
        clearButton.setHalfSize(true);
        clearButton.setDisableBackground(true);
        widgets.add("processingClearPattern", clearButton);

        cycleOutputButton = new ActionButton(
                ActionItems.S_CYCLE_PROCESSING_OUTPUT,
                action -> menu.cycleProcessingOutput());
        cycleOutputButton.setHalfSize(true);
        cycleOutputButton.setDisableBackground(true);
        widgets.add("processingCycleOutput", cycleOutputButton);

        scrollbar = widgets.addScrollBar("processingPatternModeScrollbar", Scrollbar.SMALL);
        scrollbar.setRange(0, menu.getProcessingInputSlots().length / 3 - 3, 3);
        scrollbar.setCaptureMouseWheel(false);
    }

    @Override
    public void updateBeforeRender() {
        screen.repositionSlots(SlotSemantics.PROCESSING_INPUTS);
        screen.repositionSlots(SlotSemantics.PROCESSING_OUTPUTS);

        for (int i = 0; i < menu.getProcessingInputSlots().length; i++) {
            var slot = menu.getProcessingInputSlots()[i];
            var effectiveRow = i / 3 - scrollbar.getCurrentScroll();
            slot.setActive(effectiveRow >= 0 && effectiveRow < 3);
            slot.y -= scrollbar.getCurrentScroll() * 18;
        }
        for (int i = 0; i < menu.getProcessingOutputSlots().length; i++) {
            var slot = menu.getProcessingOutputSlots()[i];
            var effectiveRow = i - scrollbar.getCurrentScroll();
            slot.setActive(effectiveRow >= 0 && effectiveRow < 3);
            slot.y -= scrollbar.getCurrentScroll() * 18;
        }

        updateTooltipVisibility();
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
        BG.dest(bounds.getX() + 8, bounds.getY() + bounds.getHeight() - 165).blit(graphics);
    }

    @Override
    public boolean onMouseWheel(Point mousePosition, double delta) {
        return scrollbar.onMouseWheel(mousePosition, delta);
    }

    private void updateTooltipVisibility() {
        widgets.setTooltipAreaEnabled(
                "processing-primary-output",
                visible && scrollbar.getCurrentScroll() == 0);
        widgets.setTooltipAreaEnabled(
                "processing-optional-output1",
                visible && scrollbar.getCurrentScroll() > 0);
        widgets.setTooltipAreaEnabled("processing-optional-output2", visible);
        widgets.setTooltipAreaEnabled("processing-optional-output3", visible);
    }

    @Override
    Icon getIcon() {
        return Icon.TAB_PROCESSING;
    }

    @Override
    Component getTabTooltip() {
        return GuiText.ProcessingPattern.text();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        scrollbar.setVisible(visible);
        clearButton.setVisibility(visible);
        cycleOutputButton.setVisibility(visible && menu.canCycleProcessingOutputs());
        screen.setSlotsHidden(SlotSemantics.PROCESSING_INPUTS, !visible);
        screen.setSlotsHidden(SlotSemantics.PROCESSING_OUTPUTS, !visible);
        updateTooltipVisibility();
    }
}
