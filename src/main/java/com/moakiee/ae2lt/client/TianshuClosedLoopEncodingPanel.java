/*
 * Layout derived from Applied Energistics 2's ProcessingEncodingPanel.
 * Copyright (c) Applied Energistics 2 contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

package com.moakiee.ae2lt.client;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.config.ActionItems;
import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.Scrollbar;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Inline closed-loop editor shown in the terminal's encoding area, mirroring the processing panel. */
final class TianshuClosedLoopEncodingPanel implements ICompositeWidget {
    private static final Blitter BG = Blitter.texture("guis/pattern_modes.png").src(0, 70, 124, 66);
    // Reuse a blank part of AE2's processing panel to remove its input-to-output arrow.
    private static final Blitter ARROW_COVER =
            Blitter.texture("guis/pattern_modes.png").src(73, 77, 23, 16);
    private static final int ARROW_X = 73;
    private static final int ARROW_Y = 25;
    private static final int PANEL_WIDTH = 124;
    private static final int PANEL_HEIGHT = 66;
    // Offsets relative to the panel anchor (JSON "closedLoopPanel": left 9 / bottom 166).
    private static final int MEMBER_X = 15;
    private static final int SLOT_Y = 8;
    private static final int OUTPUT_X = 100;
    private static final int CONTROL_X = 70;
    private static final int CONTROL_WIDTH = 26;
    private static final int EXEC_LABEL_Y = 9;
    private static final int STORED_LABEL_Y = 34;
    private static final int STATUS_Y = 58;
    private static final int STATUS_HEIGHT = 7;
    private static final float LABEL_SCALE = 0.6F;
    private static final int VISIBLE_ROWS = 3;

    private final TianshuPatternEncodingTermScreen<?> screen;
    private final TianshuPatternEncodingTermMenu menu;
    private final Scrollbar scrollbar;
    private final AE2Button detailButton;
    private final AE2Button autoFillButton;
    private final ActionButton clearButton;
    private final AETextField executionField;
    private final AETextField storedField;
    private boolean visible;
    private boolean syncing;
    private int x;
    private int y;

    TianshuClosedLoopEncodingPanel(
            TianshuPatternEncodingTermScreen<?> screen,
            WidgetContainer widgets,
            Runnable openDetail) {
        this.screen = screen;
        this.menu = screen.getMenu();

        scrollbar = widgets.addScrollBar("closedLoopScrollbar", Scrollbar.SMALL);
        scrollbar.setRange(0,
                TianshuPatternEncodingTermMenu.CLOSED_LOOP_MEMBER_SLOTS / 3 - VISIBLE_ROWS, 3);
        scrollbar.setCaptureMouseWheel(false);

        detailButton = widgets.addButton("closedLoopDetail",
                Component.translatable("ae2lt.tianshu.closed_loop.detail"), openDetail);
        detailButton.setTooltip(Tooltip.create(Component.translatable(
                "ae2lt.tianshu.closed_loop.detail.tooltip")));
        autoFillButton = widgets.addButton("closedLoopAutoFill",
                Component.translatable("ae2lt.tianshu.closed_loop.auto_fill"),
                menu::autoFillClosedLoop);
        clearButton = new ActionButton(ActionItems.S_CLOSE, menu::clearClosedLoopDraft);
        clearButton.setHalfSize(true);
        clearButton.setDisableBackground(true);
        widgets.add("closedLoopClear", clearButton);

        executionField = widgets.addTextField("closedLoopExecMultiplier");
        storedField = widgets.addTextField("closedLoopStoredMultiplier");
        for (var field : List.of(executionField, storedField)) {
            field.setMaxLength(9);
            field.setFilter(TianshuClosedLoopEncodingPanel::isPositiveIntDraft);
            field.setResponder(ignored -> submitMultipliers());
        }
        executionField.setTooltipMessage(List.of(Component.translatable(
                "ae2lt.tianshu.terminal.closed_loop.execution_seed_multiplier.tooltip")));
        storedField.setTooltipMessage(List.of(Component.translatable(
                "ae2lt.tianshu.terminal.closed_loop.stored_task_multiplier.tooltip")));
    }

    @Override
    public void setPosition(Point position) {
        x = position.getX();
        y = position.getY();
    }

    @Override
    public void setSize(int width, int height) {
    }

    @Override
    public Rect2i getBounds() {
        return new Rect2i(x, y, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    void setVisible(boolean visible) {
        this.visible = visible;
        scrollbar.setVisible(visible);
        detailButton.visible = visible;
        autoFillButton.visible = visible;
        clearButton.setVisibility(visible);
        executionField.setVisible(visible);
        storedField.setVisible(visible);
        screen.setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_MEMBER, !visible);
        screen.setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_OUTPUT_MARK, !visible);
    }

    @Override
    public void updateBeforeRender() {
        if (!visible) return;

        int scroll = scrollbar.getCurrentScroll();
        var memberSlots = menu.getClosedLoopMemberSlots();
        for (int i = 0; i < memberSlots.size(); i++) {
            var slot = memberSlots.get(i);
            int effectiveRow = i / 3 - scroll;
            boolean active = effectiveRow >= 0 && effectiveRow < VISIBLE_ROWS;
            slot.setActive(active);
            slot.x = x + MEMBER_X + i % 3 * 18;
            slot.y = y + SLOT_Y + effectiveRow * 18;
        }
        var outputSlots = menu.getClosedLoopOutputSlots();
        for (int i = 0; i < outputSlots.size(); i++) {
            var slot = outputSlots.get(i);
            int effectiveRow = i - scroll;
            boolean active = effectiveRow >= 0 && effectiveRow < VISIBLE_ROWS;
            slot.setActive(active);
            slot.x = x + OUTPUT_X;
            slot.y = y + SLOT_Y + effectiveRow * 18;
        }

        autoFillButton.active = menu.closedLoopCandidateCount > 0 || hasDiscoverableSource();
        autoFillButton.setTooltip(Tooltip.create(Component.translatable(
                "ae2lt.tianshu.closed_loop.auto_fill.tooltip",
                menu.closedLoopCandidateCount == 0 ? 0 : menu.closedLoopCandidateIndex + 1,
                menu.closedLoopCandidateCount)));
        syncFields();
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
        int panelX = bounds.getX() + x - 1;
        int panelY = bounds.getY() + y + 1;
        BG.dest(panelX, panelY).blit(graphics);
        ARROW_COVER.dest(panelX + ARROW_X, panelY + ARROW_Y).blit(graphics);
    }

    @Override
    public void drawForegroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
        drawScaledLabel(graphics, Component.translatable(
                "ae2lt.tianshu.closed_loop.execution_multiplier.short"), EXEC_LABEL_Y);
        drawScaledLabel(graphics, Component.translatable(
                "ae2lt.tianshu.closed_loop.stored_multiplier.short"), STORED_LABEL_Y);
        drawStatus(graphics);
    }

    private void drawScaledLabel(GuiGraphics graphics, Component text, int labelY) {
        var font = Minecraft.getInstance().font;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + CONTROL_X, y + labelY, 0);
        pose.scale(LABEL_SCALE, LABEL_SCALE, 1.0F);
        graphics.drawString(font, font.plainSubstrByWidth(
                text.getString(), (int) (CONTROL_WIDTH / LABEL_SCALE)), 0, 0, 0x404040, false);
        pose.popPose();
    }

    private void drawStatus(GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        var text = font.plainSubstrByWidth(
                statusText().getString(), (int) (CONTROL_WIDTH / LABEL_SCALE));
        float textWidth = font.width(text) * LABEL_SCALE;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + CONTROL_X + (CONTROL_WIDTH - textWidth) / 2.0F, y + STATUS_Y, 0);
        pose.scale(LABEL_SCALE, LABEL_SCALE, 1.0F);
        graphics.drawString(font, text, 0, 0, statusColor(), false);
        pose.popPose();
    }

    @Override
    public boolean onMouseWheel(Point mousePosition, double delta) {
        return scrollbar.onMouseWheel(mousePosition, delta);
    }

    boolean isMouseOverStatus(int mouseX, int mouseY) {
        return visible
                && mouseX >= x + CONTROL_X && mouseX < x + CONTROL_X + CONTROL_WIDTH
                && mouseY >= y + STATUS_Y && mouseY < y + STATUS_Y + STATUS_HEIGHT;
    }

    List<Component> buildStatusTooltip() {
        return List.of(
                statusText(),
                Component.translatable("ae2lt.tianshu.terminal.closed_loop.candidate",
                        menu.closedLoopCandidateCount == 0 ? 0 : menu.closedLoopCandidateIndex + 1,
                        menu.closedLoopCandidateCount).withStyle(ChatFormatting.GRAY));
    }

    private Component statusText() {
        if (menu.uploadState != 0) {
            return Component.translatable(menu.uploadState == 1
                    ? "ae2lt.tianshu.terminal.upload.success"
                    : "ae2lt.tianshu.terminal.upload.failed");
        }
        return Component.translatable("ae2lt.tianshu.closed_loop.status."
                + menu.closedLoopDraftStatus.name().toLowerCase(Locale.ROOT));
    }

    private int statusColor() {
        if (menu.uploadState != 0) return menu.uploadState == 1 ? 0x228822 : 0xAA2222;
        return switch (menu.closedLoopDraftStatus) {
            case VALID, ENCODED -> 0x228822;
            case EMPTY, NO_CANDIDATE -> 0x666666;
            case MISSING_PRIMARY_OUTPUT -> 0xAA7700;
            default -> 0xAA2222;
        };
    }

    private boolean hasDiscoverableSource() {
        var stack = screen.firstEncodedPattern();
        return !stack.isEmpty() && !(stack.getItem() instanceof ClosedLoopPatternItem);
    }

    private void syncFields() {
        syncing = true;
        try {
            if (!executionField.isFocused()) {
                var value = Integer.toString(menu.closedLoopExecutionSeedMultiplier);
                if (!executionField.getValue().equals(value)) executionField.setValue(value);
            }
            if (!storedField.isFocused()) {
                var value = Integer.toString(menu.closedLoopStoredTaskMultiplier);
                if (!storedField.getValue().equals(value)) storedField.setValue(value);
            }
        } finally {
            syncing = false;
        }
    }

    private void submitMultipliers() {
        if (syncing || executionField.getValue().isEmpty() || storedField.getValue().isEmpty()) return;
        try {
            int execution = Integer.parseInt(executionField.getValue());
            int stored = Integer.parseInt(storedField.getValue());
            if (execution >= 1 && stored >= 1) menu.setClosedLoopMultipliers(execution, stored);
        } catch (NumberFormatException ignored) {
        }
    }

    private static boolean isPositiveIntDraft(String value) {
        if (value.isEmpty()) return true;
        try {
            return Integer.parseInt(value) >= 1;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /** Shared member tooltip lines used by the terminal and the detail sub-screen. */
    static void appendMemberTooltip(List<Component> lines, TianshuPatternEncodingTermMenu menu,
                                    int index, ItemStack stack, Level level) {
        lines.add(Component.translatable("ae2lt.tianshu.closed_loop.tooltip.member",
                index + 1).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("ae2lt.tianshu.closed_loop.tooltip.copies",
                menu.closedLoopDraftSync.copies(index)).withStyle(ChatFormatting.GRAY));
        boolean macro = stack.getItem() instanceof ClosedLoopPatternItem item
                && item.hasPayload(stack) && item.readExecutionMember(stack) < 0;
        lines.add(Component.translatable("ae2lt.tianshu.closed_loop.tooltip.macro",
                Component.translatable(macro ? "gui.yes" : "gui.no"))
                .withStyle(ChatFormatting.GRAY));
        if (level == null) return;
        var details = PatternDetailsHelper.decodePattern(stack, level);
        if (details == null) return;
        lines.add(Component.translatable("ae2lt.tianshu.closed_loop.tooltip.inputs")
                .withStyle(ChatFormatting.DARK_GRAY));
        for (var input : details.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length == 0 || possible[0] == null || possible[0].what() == null) continue;
            long amount;
            try {
                amount = Math.multiplyExact(possible[0].amount(), input.getMultiplier());
            } catch (ArithmeticException ignored) {
                amount = Long.MAX_VALUE;
            }
            lines.add(Component.translatable("ae2lt.tianshu.closed_loop.tooltip.stack",
                    possible[0].what().getDisplayName(), amount).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("ae2lt.tianshu.closed_loop.tooltip.outputs")
                .withStyle(ChatFormatting.DARK_GRAY));
        for (var output : details.getOutputs()) {
            if (output == null || output.what() == null) continue;
            lines.add(Component.translatable("ae2lt.tianshu.closed_loop.tooltip.stack",
                    output.what().getDisplayName(), output.amount()).withStyle(ChatFormatting.GRAY));
        }
    }
}
