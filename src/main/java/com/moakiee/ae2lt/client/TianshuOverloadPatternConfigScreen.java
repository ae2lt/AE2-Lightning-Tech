package com.moakiee.ae2lt.client;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternEncodingType;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Popup arming the next processing encode as an overload pattern with per-slot match modes. */
final class TianshuOverloadPatternConfigScreen<M extends TianshuPatternEncodingTermMenu>
        extends AESubScreen<M, TianshuPatternEncodingTermScreen<M>> {
    private static final int ROW_ICON_Y_OFFSET = 4;
    private static final int ROW_TEXT_Y_OFFSET = 8;
    private static final int TOGGLE_LEFT = 120;
    private static final int TOGGLE_Y_OFFSET = 4;
    private static final int TOGGLE_WIDTH = 46;
    private static final int TOGGLE_HEIGHT = 16;

    private final Scrollbar scrollbar;
    private final List<Row> rows = new ArrayList<>();
    private final AE2Button[] toggleButtons = new AE2Button[TianshuPatternConfigLayout.VISIBLE_ROWS];

    TianshuOverloadPatternConfigScreen(TianshuPatternEncodingTermScreen<M> parent) {
        super(parent, "/screens/tianshu_overload_pattern_config.json");
        imageWidth = TianshuPatternConfigLayout.GUI_WIDTH;
        imageHeight = TianshuPatternConfigLayout.GUI_HEIGHT;

        widgets.add("button_back", new TabButton(Icon.BACK,
                Component.translatable("gui.back"), ignored -> returnToParent()));
        widgets.addButton("save",
                Component.translatable("ae2lt.tianshu.pattern_config.done"), this::confirm);
        scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.SMALL);

        collectRows();
    }

    private void collectRows() {
        var config = menu.getOverloadEncodingConfig();
        var inputRows = new ArrayList<Row>();
        var inputs = menu.getProcessingInputSlots();
        for (int i = 0; i < inputs.length; i++) {
            var stack = GenericStack.fromItemStack(inputs[i].getItem());
            if (stack == null || stack.what() == null) continue;
            inputRows.add(Row.slot(false, i, stack,
                    config != null && config.isInputIdOnly(i)));
        }
        var outputRows = new ArrayList<Row>();
        var outputs = menu.getProcessingOutputSlots();
        for (int i = 0; i < outputs.length; i++) {
            var stack = GenericStack.fromItemStack(outputs[i].getItem());
            if (stack == null || stack.what() == null) continue;
            outputRows.add(Row.slot(true, i, stack,
                    config != null && config.isOutputIdOnly(i)));
        }
        if (!inputRows.isEmpty()) {
            rows.add(Row.header(Component.translatable("ae2lt.tianshu.overload_config.inputs")));
            rows.addAll(inputRows);
        }
        if (!outputRows.isEmpty()) {
            rows.add(Row.header(Component.translatable("ae2lt.tianshu.overload_config.outputs")));
            rows.addAll(outputRows);
        }
    }

    @Override
    protected void init() {
        super.init();
        var modeTooltip = Tooltip.create(
                Component.translatable("ae2lt.tianshu.overload_config.mode.tooltip"));
        for (int i = 0; i < TianshuPatternConfigLayout.VISIBLE_ROWS; i++) {
            int visibleIndex = i;
            var button = new AE2Button(0, 0, TOGGLE_WIDTH, TOGGLE_HEIGHT,
                    Component.empty(), ignored -> toggleRow(visibleIndex));
            button.setTooltip(modeTooltip);
            button.visible = false;
            toggleButtons[i] = addRenderableWidget(button);
        }
        scrollbar.setHeight(TianshuPatternConfigLayout.SCROLLBAR_HEIGHT);
        scrollbar.setRange(0, Math.max(0, rows.size() - TianshuPatternConfigLayout.VISIBLE_ROWS), 2);
        setSlotsHidden(SlotSemantics.TOOLBOX, true);
    }

    private void toggleRow(int visibleIndex) {
        int index = scrollbar.getCurrentScroll() + visibleIndex;
        if (index < 0 || index >= rows.size()) return;
        var row = rows.get(index);
        if (row.header != null) return;
        row.idOnly = !row.idOnly;
    }

    private void confirm() {
        var inputIdOnly = new ArrayList<Integer>();
        var outputIdOnly = new ArrayList<Integer>();
        boolean hasSlots = false;
        for (var row : rows) {
            if (row.header != null) continue;
            hasSlots = true;
            if (!row.idOnly) continue;
            (row.output ? outputIdOnly : inputIdOnly).add(row.slotIndex);
        }
        if (hasSlots) {
            menu.armOverloadEncoding(new ProcessingPatternEncodingType.OverloadConfig(
                    toArray(inputIdOnly), toArray(outputIdOnly)));
        }
        returnToParent();
    }

    private static int[] toArray(List<Integer> values) {
        var result = new int[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i);
        return result;
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        for (var button : toggleButtons) button.visible = false;
        int textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        int scroll = scrollbar.getCurrentScroll();
        for (int visible = 0; visible < TianshuPatternConfigLayout.VISIBLE_ROWS; visible++) {
            int index = scroll + visible;
            if (index >= rows.size()) break;
            var row = rows.get(index);
            int rowY = TianshuPatternConfigLayout.HEADER_HEIGHT
                    + visible * TianshuPatternConfigLayout.ROW_HEIGHT;
            if (row.header != null) {
                graphics.drawString(font, row.header,
                        TianshuPatternConfigLayout.ROW_LEFT + 4
                                + TianshuPatternConfigLayout.ROW_CONTENT_X_OFFSET,
                        rowY + ROW_TEXT_Y_OFFSET, textColor, false);
                continue;
            }
            graphics.renderItem(row.stack.what().wrapForDisplayOrFilter(),
                    TianshuPatternConfigLayout.ROW_LEFT + 2
                            + TianshuPatternConfigLayout.ROW_CONTENT_X_OFFSET,
                    rowY + ROW_ICON_Y_OFFSET);
            var name = row.stack.what().getDisplayName();
            graphics.drawString(font, Language.getInstance().getVisualOrder(
                    font.substrByWidth(name,
                            TOGGLE_LEFT - TianshuPatternConfigLayout.ROW_LEFT - 24
                                    - TianshuPatternConfigLayout.ROW_CONTENT_X_OFFSET)),
                    TianshuPatternConfigLayout.ROW_LEFT + 22
                            + TianshuPatternConfigLayout.ROW_CONTENT_X_OFFSET,
                    rowY + ROW_TEXT_Y_OFFSET, textColor, false);
            var button = toggleButtons[visible];
            button.setMessage(Component.translatable(row.idOnly
                    ? "ae2lt.tianshu.overload_config.id_only"
                    : "ae2lt.tianshu.overload_config.strict"));
            button.setPosition(leftPos + TOGGLE_LEFT, topPos + rowY + TOGGLE_Y_OFFSET);
            button.visible = true;
        }
        if (rows.isEmpty()) {
            var text = Component.translatable("ae2lt.tianshu.pattern_config.empty");
            graphics.drawString(font, text,
                    (TianshuPatternConfigLayout.GUI_WIDTH - font.width(text)) / 2,
                    TianshuPatternConfigLayout.HEADER_HEIGHT
                            + (TianshuPatternConfigLayout.VISIBLE_ROWS
                            * TianshuPatternConfigLayout.ROW_HEIGHT) / 2 - 4,
                    0xFF777777, false);
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY,
                       int mouseX, int mouseY, float partialTicks) {
        TianshuPatternConfigLayout.drawBackground(graphics, offsetX, offsetY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY != 0 && rows.size() > TianshuPatternConfigLayout.VISIBLE_ROWS) {
            scrollbar.setCurrentScroll(scrollbar.getCurrentScroll() + (deltaY > 0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    private static final class Row {
        @Nullable final Component header;
        final boolean output;
        final int slotIndex;
        final GenericStack stack;
        boolean idOnly;

        private Row(@Nullable Component header, boolean output, int slotIndex,
                    GenericStack stack, boolean idOnly) {
            this.header = header;
            this.output = output;
            this.slotIndex = slotIndex;
            this.stack = stack;
            this.idOnly = idOnly;
        }

        static Row header(Component text) {
            return new Row(text, false, -1, null, false);
        }

        static Row slot(boolean output, int slotIndex, GenericStack stack, boolean idOnly) {
            return new Row(null, output, slotIndex, stack, idOnly);
        }
    }
}
