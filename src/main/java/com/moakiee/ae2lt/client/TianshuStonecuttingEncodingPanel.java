/*
 * Derived from Applied Energistics 2's StonecuttingEncodingPanel.
 * Copyright (c) Applied Energistics 2 contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

package com.moakiee.ae2lt.client;

import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.client.gui.Tooltip;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.Nullable;

final class TianshuStonecuttingEncodingPanel extends TianshuEncodingModePanel {
    private static final Blitter BG = Blitter.texture("guis/pattern_modes.png").src(0, 140, 124, 66);
    private static final Blitter BG_SLOT = BG.copy().src(124, 140, 20, 22);
    private static final Blitter BG_SLOT_SELECTED = BG.copy().src(124, 162, 20, 22);
    private static final Blitter BG_SLOT_HOVER = BG.copy().src(124, 184, 20, 22);

    private static final int COLUMNS = 4;
    private static final int ROWS = 2;

    private final Scrollbar scrollbar;

    TianshuStonecuttingEncodingPanel(
            TianshuPatternEncodingTermScreen<?> screen,
            WidgetContainer widgets) {
        super(screen, widgets);
        scrollbar = widgets.addScrollBar("stonecuttingPatternModeScrollbar", Scrollbar.SMALL);
        scrollbar.setRange(0, 0, COLUMNS);
        scrollbar.setCaptureMouseWheel(false);
    }

    @Override
    public void updateBeforeRender() {
        var totalRows = (menu.getStonecuttingRecipes().size() + COLUMNS - 1) / COLUMNS;
        scrollbar.setRange(0, totalRows - ROWS, ROWS);
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
        BG.dest(bounds.getX() + 8, bounds.getY() + bounds.getHeight() - 165).blit(graphics);
        drawRecipes(graphics, bounds, mouse);
    }

    private RegistryAccess getRegistryAccess() {
        return Objects.requireNonNull(Minecraft.getInstance().level).registryAccess();
    }

    private void drawRecipes(GuiGraphics graphics, Rect2i bounds, Point mouse) {
        var recipes = menu.getStonecuttingRecipes();
        var startIndex = scrollbar.getCurrentScroll() * COLUMNS;
        var endIndex = startIndex + ROWS * COLUMNS;
        var selectedRecipe = menu.getStonecuttingRecipeId();

        for (int i = startIndex; i < endIndex && i < recipes.size(); i++) {
            var slotBounds = getRecipeBounds(i - startIndex);
            var recipe = recipes.get(i);
            boolean selected = selectedRecipe != null && selectedRecipe.equals(recipe.id());

            Blitter background = selected
                    ? BG_SLOT_SELECTED
                    : mouse.isIn(slotBounds) ? BG_SLOT_HOVER : BG_SLOT;
            var renderX = bounds.getX() + slotBounds.getX();
            var renderY = bounds.getY() + slotBounds.getY();
            background.dest(renderX, renderY).blit(graphics);

            ItemStack result = recipe.value().getResultItem(getRegistryAccess());
            var itemY = renderY + (selected || mouse.isIn(slotBounds) ? 3 : 2);
            graphics.renderItem(result, renderX + 2, itemY);
            graphics.renderItemDecorations(Minecraft.getInstance().font, result, renderX + 2, itemY);
        }
    }

    @Override
    public boolean onMouseDown(Point mousePosition, int button) {
        var recipe = getRecipeAt(mousePosition);
        if (recipe == null) {
            return false;
        }
        menu.setStonecuttingRecipeId(recipe.id());
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
        return true;
    }

    @Nullable
    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        var recipe = getRecipeAt(new Point(mouseX, mouseY));
        if (recipe == null) {
            return null;
        }
        var result = recipe.value().getResultItem(getRegistryAccess());
        return new Tooltip(screen.getTooltipFromContainerItem(result));
    }

    @Nullable
    private RecipeHolder<StonecutterRecipe> getRecipeAt(Point point) {
        var recipes = menu.getStonecuttingRecipes();
        if (recipes.isEmpty()) {
            return null;
        }

        var startIndex = scrollbar.getCurrentScroll() * COLUMNS;
        var endIndex = startIndex + COLUMNS * ROWS;
        for (int i = startIndex; i < endIndex && i < recipes.size(); i++) {
            if (point.isIn(getRecipeBounds(i - startIndex))) {
                return recipes.get(i);
            }
        }
        return null;
    }

    private Rect2i getRecipeBounds(int index) {
        var column = index % COLUMNS;
        var row = index / COLUMNS;
        int slotX = x + 26 + column * BG_SLOT.getSrcWidth();
        int slotY = y + 12 + row * BG_SLOT.getSrcHeight();
        return new Rect2i(slotX, slotY, BG_SLOT.getSrcWidth(), BG_SLOT.getSrcHeight());
    }

    @Override
    public boolean onMouseWheel(Point mousePosition, double delta) {
        return scrollbar.onMouseWheel(mousePosition, delta);
    }

    @Override
    Icon getIcon() {
        return Icon.TAB_STONECUTTING;
    }

    @Override
    Component getTabTooltip() {
        return GuiText.StonecuttingPattern.text();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        scrollbar.setVisible(visible);
        screen.setSlotsHidden(SlotSemantics.STONECUTTING_INPUT, !visible);
    }
}
