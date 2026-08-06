/*
 * Derived from Applied Energistics 2's SmithingTableEncodingPanel.
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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;

final class TianshuSmithingTableEncodingPanel extends TianshuEncodingModePanel {
    private static final Blitter BG = Blitter.texture("guis/pattern_modes.png").src(128, 70, 124, 66);

    private final ActionButton clearButton;
    private final ToggleButton substitutionsButton;
    private final Slot resultSlot;

    TianshuSmithingTableEncodingPanel(
            TianshuPatternEncodingTermScreen<?> screen,
            WidgetContainer widgets) {
        super(screen, widgets);

        clearButton = new ActionButton(ActionItems.S_CLOSE, action -> menu.clear());
        clearButton.setHalfSize(true);
        clearButton.setDisableBackground(true);
        widgets.add("smithingTableClearPattern", clearButton);

        substitutionsButton = createSubstitutionButton();
        resultSlot = new Slot(new SimpleContainer(1), 0, 0, 0);
        menu.addClientSideSlot(resultSlot, SlotSemantics.SMITHING_TABLE_RESULT);
    }

    @Override
    Icon getIcon() {
        return Icon.TAB_SMITHING;
    }

    @Override
    Component getTabTooltip() {
        return GuiText.SmithingTablePattern.text();
    }

    private ToggleButton createSubstitutionButton() {
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
        widgets.add("smithingTableSubstitutions", button);
        return button;
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
        BG.dest(bounds.getX() + 8, bounds.getY() + bounds.getHeight() - 165).blit(graphics);
    }

    @Override
    public void updateBeforeRender() {
        substitutionsButton.setState(menu.substitute);

        var recipeInput = new SmithingRecipeInput(
                menu.getSmithingTableTemplateSlot().getItem(),
                menu.getSmithingTableBaseSlot().getItem(),
                menu.getSmithingTableAdditionSlot().getItem());
        var level = menu.getPlayer().level();
        var recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMITHING, recipeInput, level)
                .orElse(null);
        resultSlot.set(recipe == null
                ? ItemStack.EMPTY
                : recipe.value().assemble(recipeInput, level.registryAccess()));
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        clearButton.setVisibility(visible);
        substitutionsButton.setVisibility(visible);
        screen.setSlotsHidden(SlotSemantics.SMITHING_TABLE_TEMPLATE, !visible);
        screen.setSlotsHidden(SlotSemantics.SMITHING_TABLE_BASE, !visible);
        screen.setSlotsHidden(SlotSemantics.SMITHING_TABLE_ADDITION, !visible);
        screen.setSlotsHidden(SlotSemantics.SMITHING_TABLE_RESULT, !visible);
    }
}
