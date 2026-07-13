package com.moakiee.ae2lt.client;

import appeng.client.gui.AESubScreen;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.tianshu.MaintenanceSummarySyncPacket;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Central editor for reserves shared by every maintenance job launched by one Tianshu. */
public final class TianshuGlobalReserveScreen<M extends TianshuPatternEncodingTermMenu>
        extends AESubScreen<M, TianshuPatternEncodingTermScreen<M>> {
    private static final int FIRST_ROW = 43;
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 7;
    private EditBox search;
    private int scroll;

    public TianshuGlobalReserveScreen(TianshuPatternEncodingTermScreen<M> parent) {
        super(parent, "/screens/tianshu_maintenance_rule.json");
        hideSlots();
    }

    private void hideSlots() {
        for (var semantic : List.of(SlotSemantics.CRAFTING_GRID, SlotSemantics.CRAFTING_RESULT,
                SlotSemantics.PROCESSING_INPUTS, SlotSemantics.PROCESSING_OUTPUTS,
                SlotSemantics.SMITHING_TABLE_TEMPLATE, SlotSemantics.SMITHING_TABLE_BASE,
                SlotSemantics.SMITHING_TABLE_ADDITION, SlotSemantics.SMITHING_TABLE_RESULT,
                SlotSemantics.STONECUTTING_INPUT, SlotSemantics.BLANK_PATTERN,
                SlotSemantics.ENCODED_PATTERN, SlotSemantics.PLAYER_INVENTORY,
                SlotSemantics.PLAYER_HOTBAR)) setSlotsHidden(semantic, true);
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 8, topPos + 21, 160, 17,
                Component.translatable("gui.search"));
        search.setHint(Component.translatable("gui.search"));
        search.setResponder(value -> scroll = 0);
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> returnToParent())
                .bounds(leftPos + 128, topPos + 181, 40, 20).build());
    }

    private List<MaintenanceSummarySyncPacket.Entry> entries() {
        String needle = search != null ? search.getValue().toLowerCase(java.util.Locale.ROOT) : "";
        return menu.getMaintenanceSummary().values().stream()
                .filter(entry -> entry.globalReserve() != 0)
                .filter(entry -> needle.isEmpty() || entry.key().getDisplayName().getString()
                        .toLowerCase(java.util.Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(entry -> entry.key().getDisplayName().getString()))
                .toList();
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("ae2lt.tianshu.reserve.global_title"),
                8, 7, 0x404040, false);
        var entries = entries();
        int end = Math.min(entries.size(), scroll + VISIBLE_ROWS);
        for (int i = scroll; i < end; i++) {
            int y = FIRST_ROW + (i - scroll) * ROW_HEIGHT;
            var entry = entries.get(i);
            graphics.renderItem(entry.key().wrapForDisplayOrFilter(), 8, y);
            graphics.drawString(font, font.plainSubstrByWidth(entry.key().getDisplayName().getString(), 105),
                    29, y + 4, 0x404040, false);
            String amount = entry.globalReserve() < 0 ? "∞" : Long.toString(entry.globalReserve());
            graphics.drawString(font, amount + (entry.globalMode() == ReservedStockMatchMode.IGNORE_SECONDARY ? " *" : ""),
                    137, y + 4, 0x3355AA, false);
        }
        if (entries.isEmpty()) graphics.drawCenteredString(font,
                Component.translatable("ae2lt.tianshu.reserve.empty"), 88, 90, 0x777777);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            int row = (int) ((mouseY - topPos - FIRST_ROW) / ROW_HEIGHT);
            var entries = entries();
            int index = scroll + row;
            if (row >= 0 && row < VISIBLE_ROWS && index >= 0 && index < entries.size()
                    && mouseX >= leftPos + 7 && mouseX < leftPos + 169) {
                switchToScreen(new GlobalReserveEditScreen<>(this, entries.get(index)));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, entries().size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { returnToParent(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static final class GlobalReserveEditScreen<M extends TianshuPatternEncodingTermMenu>
            extends AESubScreen<M, TianshuGlobalReserveScreen<M>> {
        private final MaintenanceSummarySyncPacket.Entry entry;
        private EditBox amount;
        private ReservedStockMatchMode mode;

        GlobalReserveEditScreen(TianshuGlobalReserveScreen<M> parent,
                                MaintenanceSummarySyncPacket.Entry entry) {
            super(parent, "/screens/tianshu_reserve_edit.json");
            this.entry = entry;
            this.mode = entry.globalMode();
        }

        @Override
        protected void init() {
            super.init();
            amount = new EditBox(font, leftPos + 49, topPos + 31, 66, 16, Component.empty());
            amount.setValue(Long.toString(entry.globalReserve()));
            amount.setFilter(value -> value.isEmpty() || value.equals("-1") || valid(value));
            addRenderableWidget(amount);
            addRenderableWidget(Button.builder(modeLabel(), button -> {
                mode = mode == ReservedStockMatchMode.EXACT
                        ? ReservedStockMatchMode.IGNORE_SECONDARY : ReservedStockMatchMode.EXACT;
                button.setMessage(modeLabel());
            }).bounds(leftPos + 8, topPos + 54, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> returnToParent())
                    .bounds(leftPos + 88, topPos + 82, 38, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> save())
                    .bounds(leftPos + 128, topPos + 82, 38, 20).build());
        }

        private static boolean valid(String value) {
            try { return Long.parseLong(value) >= 0; }
            catch (NumberFormatException ignored) { return false; }
        }

        private Component modeLabel() {
            return Component.translatable(mode == ReservedStockMatchMode.EXACT
                    ? "ae2lt.tianshu.reserve.exact" : "ae2lt.tianshu.reserve.ignore_nbt");
        }

        private void save() {
            try {
                long parsed = Long.parseLong(amount.getValue());
                if (parsed < -1) return;
                menu.sendGlobalReserve(entry.key(), parsed, mode);
                returnToParent();
            } catch (NumberFormatException ignored) { }
        }

        @Override
        public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
            super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
            graphics.drawString(font, entry.key().getDisplayName(), 8, 7, 0x404040, false);
            graphics.drawString(font, Component.translatable("ae2lt.tianshu.reserve.amount"), 8, 35, 0x404040, false);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { returnToParent(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
