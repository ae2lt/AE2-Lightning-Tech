package com.moakiee.ae2lt.client;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.client.AEKeyRendering;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AECheckbox;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.TabButton;
import appeng.core.localization.ButtonToolTips;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceBadge;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceStatus;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.MaintenanceEditorData;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.tianshu.SaveMaintenanceRulePacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Shift-middle-click inventory-maintenance rule editor, backed by the menu-bound Tianshu. */
public final class TianshuMaintenanceRuleScreen<M extends TianshuPatternEncodingTermMenu>
        extends AESubScreen<M, TianshuPatternEncodingTermScreen<M>> {
    private static final int LIST_LEFT = 9;
    private static final int LIST_RIGHT = 208;
    private static final int FIRST_ROW_Y = 136;
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 4;

    private final Draft draft;
    private final AETextField lower;
    private final AETextField upper;
    private final AETextField perJob;
    private final AECheckbox enabled;
    private final Scrollbar scrollbar;
    private final AE2Button saveButton;
    private final AE2Button deleteButton;
    private final AE2Button checkButton;
    private final AE2Button cancelJobButton;
    private boolean deleteArmed;

    public TianshuMaintenanceRuleScreen(
            TianshuPatternEncodingTermScreen<M> parent, MaintenanceEditorData data) {
        super(parent, "/screens/tianshu_maintenance_rule.json");
        draft = new Draft(data);
        hideTerminalSlots();

        lower = numberField("lower", data.lowerThreshold());
        upper = numberField("upper", data.upperThreshold());
        perJob = numberField("perJob", data.amountPerJob());
        enabled = widgets.addCheckbox("enabled",
                Component.translatable("ae2lt.tianshu.maintenance.enabled"), () -> { });
        enabled.setSelected(data.enabled());

        scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.SMALL);
        scrollbar.setCaptureMouseWheel(false);
        checkButton = widgets.addButton("check",
                Component.translatable("ae2lt.tianshu.maintenance.check_now"), this::checkNow);
        cancelJobButton = widgets.addButton("cancelJob",
                Component.translatable("ae2lt.tianshu.maintenance.cancel_job"), this::cancelJob);
        deleteButton = widgets.addButton("delete",
                Component.translatable("ae2lt.tianshu.maintenance.delete"), this::delete);
        widgets.addButton("cancel", Component.translatable("gui.cancel"), this::returnToParent);
        saveButton = widgets.addButton("save", Component.translatable("gui.done"), this::save);
        widgets.add("back", new TabButton(
                Icon.BACK, Component.translatable("gui.back"), ignored -> returnToParent()));
    }

    private AETextField numberField(String id, long initialValue) {
        var field = widgets.addTextField(id);
        field.setMaxLength(19);
        field.setFilter(TianshuMaintenanceRuleScreen::isNonNegativeIntegerDraft);
        field.setValue(Long.toString(initialValue));
        return field;
    }

    private static boolean isNonNegativeIntegerDraft(String value) {
        if (value.isEmpty()) return true;
        try {
            return Long.parseLong(value) >= 0L;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void hideTerminalSlots() {
        for (var semantic : List.of(SlotSemantics.CRAFTING_GRID, SlotSemantics.CRAFTING_RESULT,
                SlotSemantics.PROCESSING_INPUTS, SlotSemantics.PROCESSING_OUTPUTS,
                SlotSemantics.SMITHING_TABLE_TEMPLATE, SlotSemantics.SMITHING_TABLE_BASE,
                SlotSemantics.SMITHING_TABLE_ADDITION, SlotSemantics.SMITHING_TABLE_RESULT,
                SlotSemantics.STONECUTTING_INPUT, SlotSemantics.BLANK_PATTERN,
                SlotSemantics.ENCODED_PATTERN, SlotSemantics.PLAYER_INVENTORY,
                SlotSemantics.PLAYER_HOTBAR)) {
            setSlotsHidden(semantic, true);
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        scrollbar.setRange(0, Math.max(0, draft.reserves.size() - VISIBLE_ROWS),
                Math.max(1, VISIBLE_ROWS - 1));
        saveButton.active = validationError() == null;
        deleteButton.visible = draft.data.ruleId() != null;
        deleteButton.active = draft.data.ruleId() != null;
        deleteButton.setMessage(Component.translatable(deleteArmed
                ? "ae2lt.tianshu.maintenance.confirm_delete"
                : "ae2lt.tianshu.maintenance.delete"));

        boolean existing = draft.data.ruleId() != null;
        checkButton.visible = existing;
        checkButton.active = existing && draft.data.status() != InventoryMaintenanceStatus.CRAFTING
                && draft.data.status() != InventoryMaintenanceStatus.CANCELLING;
        cancelJobButton.visible = existing;
        cancelJobButton.active = existing && (draft.data.status() == InventoryMaintenanceStatus.CRAFTING
                || draft.data.status() == InventoryMaintenanceStatus.CANCELLING);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        graphics.drawString(font, Component.translatable(draft.data.ruleId() == null
                        ? "ae2lt.tianshu.maintenance.create_title"
                        : "ae2lt.tianshu.maintenance.edit_title"),
                10, 9, 0x30343B, false);
        graphics.renderItem(draft.data.target().wrapForDisplayOrFilter(), 11, 26);
        graphics.drawString(font,
                font.plainSubstrByWidth(draft.data.target().getDisplayName().getString(), 170),
                32, 30, 0x30343B, false);
        graphics.drawString(font,
                Component.translatable("ae2lt.tianshu.maintenance.stock_and_status",
                        compactAmount(draft.data.target(), draft.data.currentStock()),
                        Component.translatable(statusKey(draft.data.status()))),
                32, 42, statusColor(draft.data.status()), false);

        graphics.drawString(font, Component.translatable("ae2lt.tianshu.maintenance.lower"),
                10, 58, 0x505760, false);
        graphics.drawString(font, Component.translatable("ae2lt.tianshu.maintenance.upper"),
                82, 58, 0x505760, false);
        graphics.drawString(font, Component.translatable("ae2lt.tianshu.maintenance.batch"),
                154, 58, 0x505760, false);

        Component validationError = validationError();
        if (validationError != null) {
            graphics.drawString(font, validationError, 10, 112, 0xB22F36, false);
        } else if (draft.data.recoveryPage()) {
            graphics.drawString(font,
                    Component.translatable("ae2lt.tianshu.maintenance.recovery_page"),
                    10, 112, 0xA73535, false);
        } else {
            graphics.drawString(font,
                    Component.translatable("ae2lt.tianshu.maintenance.topology"),
                    10, 112, 0x505760, false);
        }

        graphics.drawString(font, Component.translatable("ae2lt.tianshu.maintenance.column.item"),
                32, 125, 0x5D646D, false);
        drawRightAligned(graphics, Component.translatable("ae2lt.tianshu.maintenance.column.stock"),
                131, 125, 0x5D646D);
        drawRightAligned(graphics, Component.translatable("ae2lt.tianshu.maintenance.column.global_short"),
                170, 125, 0x5D646D);
        drawRightAligned(graphics, Component.translatable("ae2lt.tianshu.maintenance.column.rule_short"),
                205, 125, 0x5D646D);
        drawTopology(graphics, mouseX - leftPos, mouseY - topPos);
    }

    private void drawTopology(GuiGraphics graphics, int mouseX, int mouseY) {
        int start = scrollbar.getCurrentScroll();
        int end = Math.min(draft.reserves.size(), start + VISIBLE_ROWS);
        for (int index = start; index < end; index++) {
            int row = index - start;
            int y = FIRST_ROW_Y + row * ROW_HEIGHT;
            var entry = draft.reserves.get(index);
            boolean hovered = mouseY >= y && mouseY < y + ROW_HEIGHT - 1
                    && mouseX >= LIST_LEFT && mouseX < LIST_RIGHT;
            graphics.fill(LIST_LEFT, y, LIST_RIGHT, y + ROW_HEIGHT - 1,
                    hovered ? 0x553B719F : (row & 1) == 0 ? 0x1EFFFFFF : 0x12000000);
            int indent = Math.min(4, entry.depth) * 4;
            graphics.renderItem(entry.key.wrapForDisplayOrFilter(), 11 + indent, y + 1);
            graphics.drawString(font,
                    font.plainSubstrByWidth(entry.key.getDisplayName().getString(), 77 - indent),
                    31 + indent, y + 5, entry.craftable ? 0x30343B : 0xA73535, false);
            drawRightAligned(graphics, Component.literal(compactAmount(entry.key, entry.storedAmount)),
                    131, y + 5, 0x444B53);
            drawRightAligned(graphics, reserveText(entry.globalAmount, entry.globalMode),
                    170, y + 5, entry.globalAmount == 0L ? 0x7A8087 : 0x245E91);
            drawRightAligned(graphics, reserveText(entry.ruleAmount, entry.ruleMode),
                    205, y + 5, entry.ruleAmount == 0L ? 0x7A8087 : 0x794D91);
        }
    }

    private void drawRightAligned(
            GuiGraphics graphics, Component text, int right, int y, int color) {
        graphics.drawString(font, text, right - font.width(text), y, color, false);
    }

    private static Component reserveText(long amount, ReservedStockMatchMode mode) {
        String value = amount < 0L ? "∞" : Long.toString(amount);
        return Component.literal(value + (mode == ReservedStockMatchMode.IGNORE_SECONDARY ? "*" : ""));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int row = (int) ((mouseY - topPos - FIRST_ROW_Y) / ROW_HEIGHT);
            int index = scrollbar.getCurrentScroll() + row;
            if (mouseY >= topPos + FIRST_ROW_Y
                    && mouseY < topPos + FIRST_ROW_Y + VISIBLE_ROWS * ROW_HEIGHT
                    && row >= 0 && row < VISIBLE_ROWS && index < draft.reserves.size()
                    && mouseX >= leftPos + LIST_LEFT && mouseX < leftPos + LIST_RIGHT) {
                var reserve = draft.reserves.get(index);
                switchToScreen(new ReserveEditorScreen<>(this, reserve,
                        draft.data.target().equals(reserve.key) ? draft.data.variants() : List.of()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= leftPos + LIST_LEFT && mouseX < leftPos + LIST_RIGHT
                && mouseY >= topPos + FIRST_ROW_Y
                && mouseY < topPos + FIRST_ROW_Y + VISIBLE_ROWS * ROW_HEIGHT) {
            scrollbar.setCurrentScroll(scrollbar.getCurrentScroll() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        int row = (y - topPos - FIRST_ROW_Y) / ROW_HEIGHT;
        int index = scrollbar.getCurrentScroll() + row;
        if (y >= topPos + FIRST_ROW_Y && y < topPos + FIRST_ROW_Y + VISIBLE_ROWS * ROW_HEIGHT
                && row >= 0 && row < VISIBLE_ROWS && index >= 0 && index < draft.reserves.size()
                && x >= leftPos + LIST_LEFT && x < leftPos + LIST_RIGHT) {
            var reserve = draft.reserves.get(index);
            var lines = new ArrayList<Component>();
            lines.add(reserve.key.getDisplayName());
            lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.stock",
                    compactAmount(reserve.key, reserve.storedAmount)).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("ae2lt.tianshu.reserve.global_value",
                    formatReserve(reserve.globalAmount), modeLabel(reserve.globalMode))
                    .withStyle(ChatFormatting.BLUE));
            lines.add(Component.translatable("ae2lt.tianshu.reserve.rule_value",
                    formatReserve(reserve.ruleAmount), modeLabel(reserve.ruleMode))
                    .withStyle(ChatFormatting.DARK_PURPLE));
            lines.add(Component.translatable("ae2lt.tianshu.maintenance.click_edit_reserve")
                    .withStyle(ChatFormatting.DARK_GRAY));
            drawTooltip(graphics, x, y, lines);
            return;
        }
        super.renderTooltip(graphics, x, y);
    }

    private Component validationError() {
        Long parsedLower = parse(lower);
        Long parsedUpper = parse(upper);
        Long parsedPerJob = parse(perJob);
        if (parsedLower == null || parsedUpper == null || parsedPerJob == null) {
            return Component.translatable("ae2lt.tianshu.maintenance.error.number");
        }
        if (parsedUpper <= parsedLower) {
            return Component.translatable("ae2lt.tianshu.maintenance.error.thresholds");
        }
        if (parsedPerJob <= 0L) {
            return Component.translatable("ae2lt.tianshu.maintenance.error.batch");
        }
        return null;
    }

    private void save() {
        if (validationError() != null) return;
        long parsedLower = parse(lower);
        long parsedUpper = parse(upper);
        long parsedPerJob = parse(perJob);
        var edits = draft.reserves.stream().map(entry -> new SaveMaintenanceRulePacket.ReserveEdit(
                entry.key, entry.globalAmount, entry.globalMode,
                entry.ruleAmount, entry.ruleMode)).toList();
        menu.sendMaintenanceSave(new SaveMaintenanceRulePacket(
                menu.containerId, menu.tianshuSelectionRevision, draft.data.target(),
                draft.data.ruleId(), false, parsedLower, parsedUpper, parsedPerJob,
                enabled.isSelected(), edits));
        returnToParent();
    }

    private void delete() {
        if (draft.data.ruleId() == null) return;
        if (!deleteArmed) {
            deleteArmed = true;
            return;
        }
        menu.sendMaintenanceSave(new SaveMaintenanceRulePacket(
                menu.containerId, menu.tianshuSelectionRevision, draft.data.target(),
                draft.data.ruleId(), true, 0L, 1L, 1L, false, List.of()));
        returnToParent();
    }

    private void checkNow() {
        if (draft.data.ruleId() == null) return;
        menu.runMaintenanceAction(draft.data.ruleId(), false);
        returnToParent();
    }

    private void cancelJob() {
        if (draft.data.ruleId() == null) return;
        menu.runMaintenanceAction(draft.data.ruleId(), true);
        returnToParent();
    }

    private static Long parse(AETextField field) {
        try {
            return Long.parseLong(field.getValue());
        } catch (NumberFormatException ignored) {
            return null;
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

    private static String compactAmount(AEKey key, long amount) {
        return key.formatAmount(Math.max(0L, amount), AmountFormat.SLOT);
    }

    private static String formatReserve(long amount) {
        return amount < 0L ? "∞" : Long.toString(amount);
    }

    private static Component modeLabel(ReservedStockMatchMode mode) {
        return Component.translatable(mode == ReservedStockMatchMode.EXACT
                ? "ae2lt.tianshu.reserve.exact" : "ae2lt.tianshu.reserve.ignore_nbt");
    }

    private static String statusKey(InventoryMaintenanceStatus status) {
        return "ae2lt.tianshu.maintenance.status." + status.name().toLowerCase(Locale.ROOT);
    }

    private static int statusColor(InventoryMaintenanceStatus status) {
        return switch (InventoryMaintenanceBadge.from(status)) {
            case GREEN -> 0x2B8E43;
            case YELLOW -> 0x9B6A16;
            case RED -> 0xB2353B;
            case GRAY -> 0x6E7379;
        };
    }

    private static final class Draft {
        final MaintenanceEditorData data;
        final List<ReserveDraft> reserves = new ArrayList<>();

        Draft(MaintenanceEditorData data) {
            this.data = data;
            for (var entry : data.topology()) reserves.add(new ReserveDraft(entry));
        }
    }

    private static final class ReserveDraft {
        final AEKey key;
        final int depth;
        final boolean craftable;
        final long storedAmount;
        long globalAmount;
        ReservedStockMatchMode globalMode;
        long ruleAmount;
        ReservedStockMatchMode ruleMode;

        ReserveDraft(MaintenanceEditorData.TopologyEntry entry) {
            key = entry.key();
            depth = entry.depth();
            craftable = entry.craftable();
            storedAmount = entry.storedAmount();
            globalAmount = entry.globalReserve();
            globalMode = entry.globalMode();
            ruleAmount = entry.ruleReserve();
            ruleMode = entry.ruleMode();
        }
    }

    /** Second-level editor. Values are committed to the parent draft only when Done is pressed. */
    private static final class ReserveEditorScreen<M extends TianshuPatternEncodingTermMenu>
            extends AESubScreen<M, TianshuMaintenanceRuleScreen<M>> {
        private static final int VARIANT_FIRST_ROW = 120;
        private static final int VARIANT_ROW_HEIGHT = 17;
        private static final int VISIBLE_VARIANTS = 2;

        private final ReserveDraft reserve;
        private final List<MaintenanceEditorData.VariantEntry> variants;
        private final AETextField amount;
        private final AE2Button scopeButton;
        private final AE2Button modeButton;
        private final AE2Button saveButton;
        private final Scrollbar scrollbar;
        private boolean global = true;
        private long globalAmount;
        private ReservedStockMatchMode globalMode;
        private long ruleAmount;
        private ReservedStockMatchMode ruleMode;

        ReserveEditorScreen(
                TianshuMaintenanceRuleScreen<M> parent,
                ReserveDraft reserve,
                List<MaintenanceEditorData.VariantEntry> variants) {
            super(parent, "/screens/tianshu_reserve_edit.json");
            parent.hideTerminalSlots();
            this.reserve = reserve;
            this.variants = List.copyOf(variants);
            globalAmount = reserve.globalAmount;
            globalMode = reserve.globalMode;
            ruleAmount = reserve.ruleAmount;
            ruleMode = reserve.ruleMode;

            scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.SMALL);
            scrollbar.setCaptureMouseWheel(false);
            amount = widgets.addTextField("amount");
            amount.setMaxLength(19);
            amount.setFilter(ReserveEditorScreen::validDraft);
            amount.setValue(formatReserve(globalAmount).replace("∞", "-1"));
            scopeButton = widgets.addButton("scope", scopeLabel(), this::toggleScope);
            modeButton = widgets.addButton("mode", modeLabel(), this::toggleMode);
            widgets.addButton("zero", Component.literal("0"), () -> amount.setValue("0"));
            widgets.addButton("stack", Component.literal("64"), () -> amount.setValue("64"));
            widgets.addButton("infinite", Component.literal("∞"), () -> amount.setValue("-1"));
            widgets.addButton("cancel", Component.translatable("gui.cancel"), this::returnToParent);
            saveButton = widgets.addButton("save", Component.translatable("gui.done"), this::save);
            widgets.add("back", new TabButton(
                    Icon.BACK, Component.translatable("gui.back"), ignored -> returnToParent()));
        }

        private static boolean validDraft(String value) {
            if (value.isEmpty() || value.equals("-1")) return true;
            try {
                return Long.parseLong(value) >= 0L;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        private long parsedAmount() {
            try {
                long value = Long.parseLong(amount.getValue());
                return value >= -1L ? value : Long.MIN_VALUE;
            } catch (NumberFormatException ignored) {
                return Long.MIN_VALUE;
            }
        }

        private void storeVisibleDraft() {
            long parsed = parsedAmount();
            if (parsed == Long.MIN_VALUE) return;
            if (global) globalAmount = parsed;
            else ruleAmount = parsed;
        }

        private void toggleScope() {
            if (parsedAmount() == Long.MIN_VALUE) return;
            storeVisibleDraft();
            global = !global;
            amount.setValue(Long.toString(global ? globalAmount : ruleAmount));
            scopeButton.setMessage(scopeLabel());
            modeButton.setMessage(modeLabel());
        }

        private void toggleMode() {
            if (global) {
                globalMode = globalMode == ReservedStockMatchMode.EXACT
                        ? ReservedStockMatchMode.IGNORE_SECONDARY : ReservedStockMatchMode.EXACT;
            } else {
                ruleMode = ruleMode == ReservedStockMatchMode.EXACT
                        ? ReservedStockMatchMode.IGNORE_SECONDARY : ReservedStockMatchMode.EXACT;
            }
            modeButton.setMessage(modeLabel());
        }

        private Component scopeLabel() {
            return Component.translatable(global
                    ? "ae2lt.tianshu.reserve.global" : "ae2lt.tianshu.reserve.rule");
        }

        private Component modeLabel() {
            var mode = global ? globalMode : ruleMode;
            return TianshuMaintenanceRuleScreen.modeLabel(mode);
        }

        private void save() {
            if (parsedAmount() == Long.MIN_VALUE) return;
            storeVisibleDraft();
            reserve.globalAmount = globalAmount;
            reserve.globalMode = globalMode;
            reserve.ruleAmount = ruleAmount;
            reserve.ruleMode = ruleMode;
            returnToParent();
        }

        @Override
        protected void updateBeforeRender() {
            super.updateBeforeRender();
            saveButton.active = parsedAmount() != Long.MIN_VALUE;
            scopeButton.setMessage(scopeLabel());
            modeButton.setMessage(modeLabel());
            boolean showVariants = (global ? globalMode : ruleMode)
                    == ReservedStockMatchMode.IGNORE_SECONDARY && !variants.isEmpty();
            scrollbar.setVisible(showVariants);
            scrollbar.setRange(0, Math.max(0, variants.size() - VISIBLE_VARIANTS), 1);
        }

        @Override
        public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
            super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
            graphics.drawString(font, Component.translatable("ae2lt.tianshu.reserve.title"),
                    10, 9, 0x30343B, false);
            graphics.renderItem(reserve.key.wrapForDisplayOrFilter(), 11, 28);
            graphics.drawString(font,
                    font.plainSubstrByWidth(reserve.key.getDisplayName().getString(), 176),
                    32, 33, 0x30343B, false);
            graphics.drawString(font, Component.translatable("ae2lt.tianshu.reserve.current_stock",
                    reserve.storedAmount), 11, 78, 0x59616B, false);
            graphics.drawString(font, Component.translatable("ae2lt.tianshu.reserve.amount"),
                    11, 97, 0x40464E, false);

            var selectedMode = global ? globalMode : ruleMode;
            if (selectedMode == ReservedStockMatchMode.IGNORE_SECONDARY && !variants.isEmpty()) {
                graphics.drawString(font, Component.translatable("ae2lt.tianshu.reserve.variant_title"),
                        11, 111, 0x555D66, false);
                int start = scrollbar.getCurrentScroll();
                int end = Math.min(variants.size(), start + VISIBLE_VARIANTS);
                for (int index = start; index < end; index++) {
                    int y = VARIANT_FIRST_ROW + (index - start) * VARIANT_ROW_HEIGHT;
                    var variant = variants.get(index);
                    graphics.renderItem(variant.key().wrapForDisplayOrFilter(), 12, y);
                    graphics.drawString(font,
                            font.plainSubstrByWidth(variant.key().getDisplayName().getString(), 120),
                            33, y + 4, 0x3C434B, false);
                    String stock = compactAmount(variant.key(), variant.storedAmount());
                    graphics.drawString(font, stock, 202 - font.width(stock), y + 4,
                            variant.craftable() ? 0x2F6D3C : 0x5C636B, false);
                }
            } else {
                graphics.drawString(font,
                        Component.translatable("ae2lt.tianshu.reserve.exact_hint"),
                        11, 126, 0x6A7077, false);
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (mouseY >= topPos + VARIANT_FIRST_ROW
                    && mouseY < topPos + VARIANT_FIRST_ROW + VISIBLE_VARIANTS * VARIANT_ROW_HEIGHT) {
                scrollbar.setCurrentScroll(scrollbar.getCurrentScroll() - (int) Math.signum(scrollY));
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        protected void renderTooltip(GuiGraphics graphics, int x, int y) {
            int row = (y - topPos - VARIANT_FIRST_ROW) / VARIANT_ROW_HEIGHT;
            int index = scrollbar.getCurrentScroll() + row;
            var selectedMode = global ? globalMode : ruleMode;
            if (selectedMode == ReservedStockMatchMode.IGNORE_SECONDARY
                    && y >= topPos + VARIANT_FIRST_ROW
                    && y < topPos + VARIANT_FIRST_ROW + VISIBLE_VARIANTS * VARIANT_ROW_HEIGHT
                    && row >= 0 && row < VISIBLE_VARIANTS
                    && index >= 0 && index < variants.size()
                    && x >= leftPos + 11 && x < leftPos + 207) {
                var variant = variants.get(index);
                var lines = new ArrayList<>(AEKeyRendering.getTooltip(variant.key()));
                lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.stock",
                        compactAmount(variant.key(), variant.storedAmount()))
                        .withStyle(ChatFormatting.GRAY));
                if (variant.craftable()) {
                    lines.add(ButtonToolTips.Craftable.text().withStyle(ChatFormatting.GREEN));
                }
                drawTooltip(graphics, x, y, lines);
                return;
            }
            super.renderTooltip(graphics, x, y);
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
