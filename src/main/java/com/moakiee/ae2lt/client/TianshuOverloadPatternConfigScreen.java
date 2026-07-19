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
    private static final int GUI_WIDTH = 195;
    private static final int HEADER_HEIGHT = 20;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 5;
    private static final int FOOTER_HEIGHT = 26;
    private static final int ROW_LEFT = 8;
    private static final int ROW_RIGHT = 169;
    private static final int TOGGLE_LEFT = 120;
    private static final int TOGGLE_WIDTH = 46;
    private static final int TOGGLE_HEIGHT = 16;

    private final Scrollbar scrollbar;
    private final List<Row> rows = new ArrayList<>();
    private final AE2Button[] toggleButtons = new AE2Button[VISIBLE_ROWS];

    TianshuOverloadPatternConfigScreen(TianshuPatternEncodingTermScreen<M> parent) {
        super(parent, "/screens/tianshu_overload_pattern_config.json");
        imageWidth = GUI_WIDTH;
        imageHeight = HEADER_HEIGHT + VISIBLE_ROWS * ROW_HEIGHT + FOOTER_HEIGHT;

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
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int visibleIndex = i;
            var button = new AE2Button(0, 0, TOGGLE_WIDTH, TOGGLE_HEIGHT,
                    Component.empty(), ignored -> toggleRow(visibleIndex));
            button.setTooltip(modeTooltip);
            button.visible = false;
            toggleButtons[i] = addRenderableWidget(button);
        }
        scrollbar.setHeight(VISIBLE_ROWS * ROW_HEIGHT - 2);
        scrollbar.setRange(0, Math.max(0, rows.size() - VISIBLE_ROWS), 2);
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
        for (int visible = 0; visible < VISIBLE_ROWS; visible++) {
            int index = scroll + visible;
            if (index >= rows.size()) break;
            var row = rows.get(index);
            int rowY = HEADER_HEIGHT + visible * ROW_HEIGHT;
            if (row.header != null) {
                graphics.drawString(font, row.header, ROW_LEFT + 4, rowY + 6, textColor, false);
                continue;
            }
            graphics.renderItem(row.stack.what().wrapForDisplayOrFilter(), ROW_LEFT + 2, rowY + 2);
            var name = row.stack.what().getDisplayName();
            graphics.drawString(font, Language.getInstance().getVisualOrder(
                    font.substrByWidth(name, TOGGLE_LEFT - ROW_LEFT - 24)),
                    ROW_LEFT + 22, rowY + 6, textColor, false);
            var button = toggleButtons[visible];
            button.setMessage(Component.translatable(row.idOnly
                    ? "ae2lt.tianshu.overload_config.id_only"
                    : "ae2lt.tianshu.overload_config.strict"));
            button.setPosition(leftPos + TOGGLE_LEFT, topPos + rowY + 1);
            button.visible = true;
        }
        if (rows.isEmpty()) {
            var text = Component.translatable("ae2lt.tianshu.pattern_config.empty");
            graphics.drawString(font, text, (GUI_WIDTH - font.width(text)) / 2,
                    HEADER_HEIGHT + (VISIBLE_ROWS * ROW_HEIGHT) / 2 - 4, 0xFF777777, false);
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY,
                       int mouseX, int mouseY, float partialTicks) {
        int bottom = offsetY + imageHeight;
        graphics.fill(offsetX, offsetY, offsetX + GUI_WIDTH, bottom, 0xFFC6C6D2);
        graphics.fill(offsetX, offsetY, offsetX + GUI_WIDTH, offsetY + 1, 0xFFFFFFFF);
        graphics.fill(offsetX, offsetY, offsetX + 1, bottom, 0xFFFFFFFF);
        graphics.fill(offsetX + GUI_WIDTH - 1, offsetY + 1, offsetX + GUI_WIDTH, bottom, 0xFF555563);
        graphics.fill(offsetX + 1, bottom - 1, offsetX + GUI_WIDTH, bottom, 0xFF555563);
        int scroll = scrollbar == null ? 0 : scrollbar.getCurrentScroll();
        for (int visible = 0; visible < VISIBLE_ROWS; visible++) {
            int index = scroll + visible;
            int top = offsetY + HEADER_HEIGHT + visible * ROW_HEIGHT;
            boolean header = index < rows.size() && rows.get(index).header != null;
            int color = header ? 0xFFA0A2B8 : (visible & 1) == 0 ? 0xFFB4B5C6 : 0xFF989AAC;
            graphics.fill(offsetX + ROW_LEFT - 1, top, offsetX + ROW_RIGHT, top + ROW_HEIGHT - 1, color);
            graphics.fill(offsetX + ROW_LEFT - 1, top, offsetX + ROW_RIGHT, top + 1, 0xFFE8E8F0);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY != 0 && rows.size() > VISIBLE_ROWS) {
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
