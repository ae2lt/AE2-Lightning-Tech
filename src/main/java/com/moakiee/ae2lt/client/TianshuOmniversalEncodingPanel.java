/*
 * Layout derived from Applied Energistics 2's ProcessingEncodingPanel.
 * Copyright (c) Applied Energistics 2 contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.moakiee.ae2lt.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.config.ActionItems;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.Scrollbar;
import com.moakiee.ae2lt.integration.useless.UselessModCompat;
import com.moakiee.ae2lt.logic.tianshu.terminal.OmniversalPatternDraft;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/** Editable inputs, outputs and mold filters, validated together when encoding. */
final class TianshuOmniversalEncodingPanel implements ICompositeWidget {
    private static final int ROWS = 3;
    private static final Blitter BG = Blitter.texture("guis/pattern_modes.png").src(0, 70, 124, 66);
    private static final Blitter ARROW_COVER = Blitter.texture("guis/pattern_modes.png").src(73, 77, 23, 16);
    private static final Blitter MOLD_SLOT = Blitter.texture("guis/pattern_modes.png").src(15, 76, 18, 18);
    private final TianshuPatternEncodingTermMenu menu;
    private final TianshuPatternEncodingTermScreen<?> screen;
    private final Scrollbar scrollbar;
    private final ActionButton clearButton;
    private OmniversalPatternDraft observedDraft;
    private long observedGeneration = Long.MIN_VALUE;
    private UselessModCompat.Preview preview = UselessModCompat.Preview.EMPTY;
    private boolean visible;
    private int x;
    private int y;

    TianshuOmniversalEncodingPanel(TianshuPatternEncodingTermScreen<?> screen,
                                  WidgetContainer widgets) {
        this.screen = screen;
        menu = screen.getMenu();
        scrollbar = widgets.addScrollBar("omniversalScrollbar", Scrollbar.SMALL);
        scrollbar.setCaptureMouseWheel(false);
        scrollbar.setRange(0, menu.getOmniversalInputSlots().length / 3 - ROWS, 1);
        clearButton = new ActionButton(ActionItems.S_CLOSE, menu::clearOmniversalDraft);
        clearButton.setHalfSize(true);
        clearButton.setDisableBackground(true);
        widgets.add("omniversalClear", clearButton);
    }

    @Override public void setPosition(Point position) { x = position.getX(); y = position.getY(); }
    @Override public void setSize(int width, int height) { }
    @Override public Rect2i getBounds() { return new Rect2i(x, y, 124, 66); }
    @Override public boolean isVisible() { return visible; }

    void setVisible(boolean visible) {
        this.visible = visible;
        scrollbar.setVisible(visible);
        clearButton.setVisibility(visible);
        screen.setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_OMNIVERSAL_INPUTS, !visible);
        screen.setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_OMNIVERSAL_OUTPUTS, !visible);
        screen.setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_OMNIVERSAL_MOLDS, !visible);
    }

    @Override
    public void updateBeforeRender() {
        if (!visible) return;
        long generation = UselessModCompat.recipeGeneration();
        if (!menu.omniversalDraft.equals(observedDraft) || generation != observedGeneration) {
            observedDraft = menu.omniversalDraft;
            observedGeneration = generation;
            preview = UselessModCompat.preview(observedDraft, Minecraft.getInstance().level);
        }
        int scroll = scrollbar.getCurrentScroll();
        for (int i = 0; i < menu.getOmniversalInputSlots().length; i++) {
            var slot = menu.getOmniversalInputSlots()[i];
            int row = i / 3 - scroll;
            slot.setActive(row >= 0 && row < ROWS);
            slot.x = x + columnX(i % 3);
            slot.y = y + 8 + row * 18;
        }
        for (int i = 0; i < menu.getOmniversalOutputSlots().length; i++) {
            var slot = menu.getOmniversalOutputSlots()[i];
            int row = i - scroll;
            slot.setActive(row >= 0 && row < ROWS);
            slot.x = x + columnX(4);
            slot.y = y + 8 + row * 18;
        }
        for (int i = 0; i < menu.getOmniversalMoldSlots().length; i++) {
            var slot = menu.getOmniversalMoldSlots()[i];
            int row = i - scroll;
            slot.setActive(row >= 0 && row < ROWS);
            slot.x = x + columnX(3);
            slot.y = y + 8 + row * 18;
        }
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
        int px = bounds.getX() + x, py = bounds.getY() + y;
        BG.dest(px - 1, py + 1).blit(graphics);
        ARROW_COVER.dest(px - 1 + 73, py + 1 + 25).blit(graphics);
        for (int row = 0; row < ROWS; row++) {
            MOLD_SLOT.dest(px + columnX(3) - 1, py + 7 + row * 18).blit(graphics);
        }
    }

    List<Component> tooltipAt(int mouseX, int mouseY) {
        if (!visible || mouseX < x || mouseX >= x + 124 || mouseY < y || mouseY >= y + 66) return null;
        int row = (mouseY - y - 8) / 18;
        if (mouseY >= y + 8 && row < ROWS) {
            for (int column = 0; column < 5; column++) {
                if (mouseX < x + columnX(column) || mouseX >= x + columnX(column) + 16) continue;
                if (column != 3) return null; // AE2 supplies the editable fake-slot tooltip.
                var stack = moldAt(scrollbar.getCurrentScroll() + row);
                if (stack != null) {
                    var lines = new ArrayList<>(AEKeyRendering.getTooltip(stack.what()));
                    lines.add(Component.literal(stack.what().formatAmount(stack.amount(), AmountFormat.FULL))
                            .withStyle(ChatFormatting.GRAY));
                    if (column == 3) lines.add(Component.translatable("ae2lt.tianshu.omniversal.molds.tooltip")
                            .withStyle(ChatFormatting.GOLD));
                    return lines;
                }
            }
        }
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable(menu.omniversalStatus));
        if (!preview.recipeId().isEmpty()) lines.add(Component.literal(preview.recipeId())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("ae2lt.tianshu.omniversal.encode_upload.tooltip")
                .withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private GenericStack moldAt(int row) {
        return row < menu.getOmniversalMoldSlots().length
                ? GenericStack.fromItemStack(menu.getOmniversalMoldSlots()[row].getItem()) : null;
    }

    private static int columnX(int column) { return column < 3 ? 15 + column * 18 : column == 3 ? 77 : 100; }

    @Override
    public boolean onMouseWheel(Point mousePosition, double delta) {
        return scrollbar.onMouseWheel(mousePosition, delta);
    }
}
