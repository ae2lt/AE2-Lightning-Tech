/*
 * Derived from Applied Energistics 2's CraftingEncodingPanel.
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
import appeng.client.gui.widgets.ToggleButton;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

final class TianshuCraftingEncodingPanel extends TianshuEncodingModePanel {
    private static final Blitter BG = Blitter.texture("guis/pattern_modes.png").src(0, 0, 124, 66);

    private final ActionButton clearButton;
    private final ToggleButton substitutionsButton;
    private final ToggleButton fluidSubstitutionsButton;

    TianshuCraftingEncodingPanel(
            TianshuPatternEncodingTermScreen<?> screen,
            WidgetContainer widgets) {
        super(screen, widgets);

        clearButton = new ActionButton(ActionItems.S_CLOSE, action -> menu.clear());
        clearButton.setHalfSize(true);
        clearButton.setDisableBackground(true);
        widgets.add("craftingClearPattern", clearButton);

        substitutionsButton = createCraftingSubstitutionButton();
        fluidSubstitutionsButton = createCraftingFluidSubstitutionButton();
    }

    @Override
    Icon getIcon() {
        return Icon.TAB_CRAFTING;
    }

    @Override
    Component getTabTooltip() {
        return GuiText.CraftingPattern.text();
    }

    private ToggleButton createCraftingSubstitutionButton() {
        var button = new ToggleButton(
                Icon.S_SUBSTITUTION_ENABLED,
                Icon.S_SUBSTITUTION_DISABLED,
                menu::setSubstitute);
        button.setHalfSize(true);
        button.setDisableBackground(true);
        button.setTooltipOn(List.of(
                ButtonToolTips.SubstitutionsOn.text(),
                ButtonToolTips.SubstitutionsDescEnabled.text()));
        button.setTooltipOff(List.of(
                ButtonToolTips.SubstitutionsOff.text(),
                ButtonToolTips.SubstitutionsDescDisabled.text()));
        widgets.add("craftingSubstitutions", button);
        return button;
    }

    private ToggleButton createCraftingFluidSubstitutionButton() {
        var button = new ToggleButton(
                Icon.S_FLUID_SUBSTITUTION_ENABLED,
                Icon.S_FLUID_SUBSTITUTION_DISABLED,
                menu::setSubstituteFluids);
        button.setHalfSize(true);
        button.setDisableBackground(true);
        button.setTooltipOn(List.of(
                ButtonToolTips.FluidSubstitutions.text(),
                ButtonToolTips.FluidSubstitutionsDescEnabled.text()));
        button.setTooltipOff(List.of(
                ButtonToolTips.FluidSubstitutions.text(),
                ButtonToolTips.FluidSubstitutionsDescDisabled.text()));
        widgets.add("craftingFluidSubstitutions", button);
        return button;
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
        BG.dest(bounds.getX() + 8, bounds.getY() + bounds.getHeight() - 165).blit(graphics);

        var absoluteMouseX = bounds.getX() + mouse.getX();
        var absoluteMouseY = bounds.getY() + mouse.getY();
        if (menu.substituteFluids
                && fluidSubstitutionsButton.isMouseOver(absoluteMouseX, absoluteMouseY)) {
            for (var slotIndex : menu.slotsSupportingFluidSubstitution) {
                drawSlotGreenBackground(bounds, graphics, menu.getCraftingGridSlots()[slotIndex]);
            }
        }
    }

    private void drawSlotGreenBackground(Rect2i bounds, GuiGraphics graphics, Slot slot) {
        int slotX = bounds.getX() + slot.x;
        int slotY = bounds.getY() + slot.y;
        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF7AC25F);
    }

    @Override
    public void updateBeforeRender() {
        substitutionsButton.setState(menu.substitute);
        fluidSubstitutionsButton.setState(menu.substituteFluids);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        clearButton.setVisibility(visible);
        substitutionsButton.setVisibility(visible);
        fluidSubstitutionsButton.setVisibility(visible);
        screen.setSlotsHidden(SlotSemantics.CRAFTING_GRID, !visible);
        screen.setSlotsHidden(SlotSemantics.CRAFTING_RESULT, !visible);
    }
}
