package com.moakiee.ae2lt.client;

import appeng.client.gui.AESubScreen;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.MaintenanceEditorData;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.tianshu.SaveMaintenanceRulePacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Shift-middle-click inventory-maintenance rule editor, backed by the menu-bound Tianshu. */
public final class TianshuMaintenanceRuleScreen<M extends TianshuPatternEncodingTermMenu> extends AESubScreen<
        M, TianshuPatternEncodingTermScreen<M>> {
    private static final int FIRST_ROW_Y = 91;
    private static final int ROW_HEIGHT = 17;
    private static final int VISIBLE_ROWS = 5;

    private final Draft draft;
    private EditBox lower;
    private EditBox upper;
    private EditBox perJob;
    private Button enabled;
    private int scroll;

    public TianshuMaintenanceRuleScreen(
            TianshuPatternEncodingTermScreen<M> parent, MaintenanceEditorData data) {
        super(parent, "/screens/tianshu_maintenance_rule.json");
        this.draft = new Draft(data);
        hideTerminalSlots();
    }

    private void hideTerminalSlots() {
        setSlotsHidden(SlotSemantics.CRAFTING_GRID, true);
        setSlotsHidden(SlotSemantics.CRAFTING_RESULT, true);
        setSlotsHidden(SlotSemantics.PROCESSING_INPUTS, true);
        setSlotsHidden(SlotSemantics.PROCESSING_OUTPUTS, true);
        setSlotsHidden(SlotSemantics.SMITHING_TABLE_TEMPLATE, true);
        setSlotsHidden(SlotSemantics.SMITHING_TABLE_BASE, true);
        setSlotsHidden(SlotSemantics.SMITHING_TABLE_ADDITION, true);
        setSlotsHidden(SlotSemantics.SMITHING_TABLE_RESULT, true);
        setSlotsHidden(SlotSemantics.STONECUTTING_INPUT, true);
        setSlotsHidden(SlotSemantics.BLANK_PATTERN, true);
        setSlotsHidden(SlotSemantics.ENCODED_PATTERN, true);
        setSlotsHidden(SlotSemantics.PLAYER_INVENTORY, true);
        setSlotsHidden(SlotSemantics.PLAYER_HOTBAR, true);
    }

    @Override
    protected void init() {
        super.init();
        lower = numberBox(76, 22, draft.lower);
        upper = numberBox(76, 43, draft.upper);
        perJob = numberBox(76, 64, draft.perJob);
        enabled = addRenderableWidget(Button.builder(enabledLabel(), button -> {
            draft.enabled = !draft.enabled;
            button.setMessage(enabledLabel());
        }).bounds(leftPos + 132, topPos + 22, 36, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> returnToParent())
                .bounds(leftPos + 51, topPos + 181, 38, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> save())
                .bounds(leftPos + 91, topPos + 181, 38, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("ae2lt.tianshu.maintenance.delete"), button -> delete())
                .bounds(leftPos + 8, topPos + 181, 41, 20).build());
    }

    private EditBox numberBox(int x, int y, long value) {
        var box = new EditBox(font, leftPos + x, topPos + y, 51, 16, Component.empty());
        box.setValue(Long.toString(value));
        box.setFilter(TianshuMaintenanceRuleScreen::isNonNegativeInteger);
        addRenderableWidget(box);
        return box;
    }

    private static boolean isNonNegativeInteger(String value) {
        if (value.isEmpty()) return true;
        try { return Long.parseLong(value) >= 0; }
        catch (NumberFormatException ignored) { return false; }
    }

    private Component enabledLabel() {
        return Component.translatable(draft.enabled ? "options.on" : "options.off");
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        graphics.drawString(font, draft.data.target().getDisplayName(), 8, 7, 0x404040, false);
        graphics.drawString(font, Component.translatable("ae2lt.tianshu.maintenance.lower"), 8, 27, 0x404040, false);
        graphics.drawString(font, Component.translatable("ae2lt.tianshu.maintenance.upper"), 8, 48, 0x404040, false);
        graphics.drawString(font, Component.translatable("ae2lt.tianshu.maintenance.batch"), 8, 69, 0x404040, false);
        graphics.drawString(font, Component.translatable("ae2lt.tianshu.maintenance.topology"), 8, 83, 0x404040, false);
        drawTopology(graphics, mouseX - leftPos, mouseY - topPos);
        if (draft.data.recoveryPage()) {
            graphics.drawCenteredString(font,
                    Component.translatable("ae2lt.tianshu.maintenance.recovery_page"),
                    88, 171, 0xAA2222);
        }
    }

    private void drawTopology(GuiGraphics graphics, int mouseX, int mouseY) {
        int end = Math.min(draft.reserves.size(), scroll + VISIBLE_ROWS);
        for (int index = scroll; index < end; index++) {
            int row = index - scroll;
            int y = FIRST_ROW_Y + row * ROW_HEIGHT;
            var entry = draft.reserves.get(index);
            if (mouseY >= y && mouseY < y + 16 && mouseX >= 7 && mouseX < 169) {
                graphics.fill(7, y, 169, y + 16, 0x334477AA);
            }
            graphics.renderItem(entry.key.wrapForDisplayOrFilter(), 8 + Math.min(4, entry.depth) * 5, y);
            graphics.drawString(font, font.plainSubstrByWidth(entry.key.getDisplayName().getString(), 72),
                    30 + Math.min(4, entry.depth) * 5, y + 4, entry.craftable ? 0x404040 : 0xAA3333, false);
            graphics.drawString(font, reserveText("G", entry.globalAmount, entry.globalMode),
                    113, y + 4, entry.globalAmount == 0 ? 0x888888 : 0x3355AA, false);
            graphics.drawString(font, reserveText("R", entry.ruleAmount, entry.ruleMode),
                    143, y + 4, entry.ruleAmount == 0 ? 0x888888 : 0x883388, false);
        }
    }

    private Component reserveText(String prefix, long amount, ReservedStockMatchMode mode) {
        String value = amount < 0 ? "∞" : Long.toString(amount);
        return Component.literal(prefix + ":" + value + (mode == ReservedStockMatchMode.IGNORE_SECONDARY ? "*" : ""));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = (int) ((mouseY - topPos - FIRST_ROW_Y) / ROW_HEIGHT);
        if ((button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
                && row >= 0 && row < VISIBLE_ROWS) {
            int index = scroll + row;
            if (index < draft.reserves.size() && mouseX >= leftPos + 7 && mouseX < leftPos + 169) {
                switchToScreen(new ReserveEditorScreen<>(this, draft.reserves.get(index),
                        draft.data.target().equals(draft.reserves.get(index).key)
                                ? draft.data.variants() : List.of()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= topPos + FIRST_ROW_Y && mouseY < topPos + FIRST_ROW_Y + VISIBLE_ROWS * ROW_HEIGHT) {
            int max = Math.max(0, draft.reserves.size() - VISIBLE_ROWS);
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            returnToParent();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void save() {
        Long parsedLower = parse(lower);
        Long parsedUpper = parse(upper);
        Long parsedPerJob = parse(perJob);
        if (parsedLower == null || parsedUpper == null || parsedPerJob == null
                || parsedUpper <= parsedLower || parsedPerJob <= 0) return;
        var edits = draft.reserves.stream().map(entry -> new SaveMaintenanceRulePacket.ReserveEdit(
                entry.key, entry.globalAmount, entry.globalMode, entry.ruleAmount, entry.ruleMode)).toList();
        menu.sendMaintenanceSave(new SaveMaintenanceRulePacket(
                menu.containerId, menu.tianshuSelectionRevision, draft.data.target(),
                draft.data.ruleId(), false, parsedLower, parsedUpper, parsedPerJob, draft.enabled, edits));
        returnToParent();
    }

    private void delete() {
        menu.sendMaintenanceSave(new SaveMaintenanceRulePacket(
                menu.containerId, menu.tianshuSelectionRevision, draft.data.target(),
                draft.data.ruleId(), true, 0, 1, 1, false, List.of()));
        returnToParent();
    }

    private static Long parse(EditBox box) {
        try { return Long.parseLong(box.getValue()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static final class Draft {
        final MaintenanceEditorData data;
        final List<ReserveDraft> reserves = new ArrayList<>();
        long lower;
        long upper;
        long perJob;
        boolean enabled;

        Draft(MaintenanceEditorData data) {
            this.data = data;
            lower = data.lowerThreshold();
            upper = data.upperThreshold();
            perJob = data.amountPerJob();
            enabled = data.enabled();
            for (var entry : data.topology()) reserves.add(new ReserveDraft(entry));
        }
    }

    private static final class ReserveDraft {
        final appeng.api.stacks.AEKey key;
        final int depth;
        final boolean craftable;
        long globalAmount;
        ReservedStockMatchMode globalMode;
        long ruleAmount;
        ReservedStockMatchMode ruleMode;

        ReserveDraft(MaintenanceEditorData.TopologyEntry entry) {
            key = entry.key();
            depth = entry.depth();
            craftable = entry.craftable();
            globalAmount = entry.globalReserve();
            globalMode = entry.globalMode();
            ruleAmount = entry.ruleReserve();
            ruleMode = entry.ruleMode();
        }
    }

    private static final class ReserveEditorScreen<M extends TianshuPatternEncodingTermMenu> extends AESubScreen<
            M, TianshuMaintenanceRuleScreen<M>> {
        private final ReserveDraft reserve;
        private final List<MaintenanceEditorData.VariantEntry> variants;
        private EditBox amount;
        private boolean global = true;
        private ReservedStockMatchMode mode;

        ReserveEditorScreen(TianshuMaintenanceRuleScreen<M> parent, ReserveDraft reserve,
                            List<MaintenanceEditorData.VariantEntry> variants) {
            super(parent, "/screens/tianshu_reserve_edit.json");
            this.reserve = reserve;
            this.variants = variants;
            this.mode = reserve.globalMode;
            parent.hideTerminalSlots();
        }

        @Override
        protected void init() {
            super.init();
            amount = new EditBox(font, leftPos + 49, topPos + 31, 66, 16, Component.empty());
            amount.setValue(formatAmount(reserve.globalAmount));
            amount.setFilter(value -> value.isEmpty() || value.equals("-1") || isNonNegativeInteger(value));
            addRenderableWidget(amount);
            addRenderableWidget(Button.builder(scopeLabel(), button -> {
                storeCurrent();
                global = !global;
                mode = global ? reserve.globalMode : reserve.ruleMode;
                amount.setValue(formatAmount(global ? reserve.globalAmount : reserve.ruleAmount));
                button.setMessage(scopeLabel());
            }).bounds(leftPos + 8, topPos + 54, 72, 20).build());
            addRenderableWidget(Button.builder(modeLabel(), button -> {
                mode = mode == ReservedStockMatchMode.EXACT
                        ? ReservedStockMatchMode.IGNORE_SECONDARY : ReservedStockMatchMode.EXACT;
                button.setMessage(modeLabel());
            }).bounds(leftPos + 82, topPos + 54, 86, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> returnToParent())
                    .bounds(leftPos + 88, topPos + 82, 38, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
                storeCurrent();
                returnToParent();
            }).bounds(leftPos + 128, topPos + 82, 38, 20).build());
        }

        private void storeCurrent() {
            long parsed;
            try { parsed = Long.parseLong(amount.getValue()); }
            catch (NumberFormatException ignored) { return; }
            if (parsed < -1) return;
            if (global) {
                reserve.globalAmount = parsed;
                reserve.globalMode = mode;
            } else {
                reserve.ruleAmount = parsed;
                reserve.ruleMode = mode;
            }
        }

        private Component scopeLabel() {
            return Component.translatable(global
                    ? "ae2lt.tianshu.reserve.global" : "ae2lt.tianshu.reserve.rule");
        }

        private Component modeLabel() {
            return Component.translatable(mode == ReservedStockMatchMode.EXACT
                    ? "ae2lt.tianshu.reserve.exact" : "ae2lt.tianshu.reserve.ignore_nbt");
        }

        private static String formatAmount(long amount) { return Long.toString(amount); }

        @Override
        public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
            super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
            graphics.drawString(font, reserve.key.getDisplayName(), 8, 7, 0x404040, false);
            graphics.drawString(font, Component.translatable("ae2lt.tianshu.reserve.amount"), 8, 35, 0x404040, false);
            if (!variants.isEmpty()) {
                long stock = variants.stream().mapToLong(MaintenanceEditorData.VariantEntry::storedAmount).sum();
                graphics.drawString(font, Component.translatable(
                        "ae2lt.tianshu.reserve.variants", variants.size(), stock), 8, 76, 0x666666, false);
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                returnToParent();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
