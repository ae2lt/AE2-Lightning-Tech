package com.moakiee.ae2lt.client;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.api.client.AEKeyRendering;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.TabButton;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceBadge;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceStatus;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.tianshu.MaintenanceSummarySyncPacket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Inventory-maintenance and Tianshu-wide reserved-stock overview. */
public final class TianshuGlobalReserveScreen<M extends TianshuPatternEncodingTermMenu>
        extends AESubScreen<M, TianshuPatternEncodingTermScreen<M>> {
    private static final int LIST_LEFT = 9;
    private static final int LIST_RIGHT = 187;
    private static final int LIST_CENTER_X = (LIST_LEFT + LIST_RIGHT) / 2;
    private static final int CONTENT_TEXT_LEFT = 37;
    private static final int STOCK_COLUMN_RIGHT = 143;
    private static final int VALUE_COLUMN_RIGHT = 184;
    private static final int FIRST_ROW = 64;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 6;
    private static final int EMPTY_TEXT_Y = 121;

    private final AETextField search;
    private final Scrollbar scrollbar;
    private final Button rulesButton;
    private final Button reservesButton;
    private final AETextField reserveAmount;
    private final Button addReserveButton;
    private final boolean restoreMaintainableView;
    private View view = View.RULES;
    private boolean awaitingRuleEditor;
    private int requestedRuleEditorRevision;

    public TianshuGlobalReserveScreen(TianshuPatternEncodingTermScreen<M> parent) {
        super(parent, "/screens/tianshu_inventory_overview.json");
        restoreMaintainableView = menu.maintainableView;
        if (restoreMaintainableView) menu.setMaintainableViewTemporarily(false);

        scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.SMALL);
        scrollbar.setCaptureMouseWheel(false);
        search = widgets.addTextField("search");
        search.setPlaceholder(GuiText.SearchPlaceholder.text());
        search.setResponder(ignored -> scrollbar.setCurrentScroll(0));
        rulesButton = widgets.addButton("rules", tabLabel(View.RULES), () -> selectView(View.RULES));
        reservesButton = widgets.addButton(
                "reserves", tabLabel(View.RESERVES), () -> selectView(View.RESERVES));
        reserveAmount = widgets.addTextField("reserveAmount");
        reserveAmount.setMaxLength(19);
        reserveAmount.setFilter(TianshuGlobalReserveScreen::validAddAmountDraft);
        reserveAmount.setPlaceholder(Component.translatable(
                "ae2lt.tianshu.reserve.add_amount"));
        reserveAmount.setValue("1");
        addReserveButton = widgets.addButton("addReserve",
                Component.translatable("ae2lt.tianshu.reserve.add"), this::addMarkedReserve);
        widgets.add("back", new TabButton(
                Icon.ARROW_LEFT, Component.translatable("gui.back"), ignored -> returnToParent()));
    }

    @Override
    public void init() {
        // Slot positions belong to the shared menu. Defer hiding until switchToScreen has saved
        // the terminal's positions so returning can restore them.
        hideSlots();
        super.init();
        updateAddControls();
    }

    @Override
    protected void onReturnToParent() {
        restoreParentViewMode();
    }

    private void restoreParentViewMode() {
        if (restoreMaintainableView) menu.setMaintainableViewTemporarily(true);
    }

    void hideSlots() {
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

    private void selectView(View selected) {
        if (view == selected) return;
        view = selected;
        scrollbar.setCurrentScroll(0);
        updateTabLabels();
        updateAddControls();
    }

    private Component tabLabel(View tab) {
        return Component.translatable(tab.translationKey)
                .withStyle(tab == view ? ChatFormatting.AQUA : ChatFormatting.WHITE);
    }

    private void updateTabLabels() {
        rulesButton.setMessage(tabLabel(View.RULES));
        reservesButton.setMessage(tabLabel(View.RESERVES));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (awaitingRuleEditor
                && menu.getMaintenanceEditorRevision() != requestedRuleEditorRevision
                && menu.getMaintenanceEditorData() != null) {
            awaitingRuleEditor = false;
            switchToScreen(new TianshuMaintenanceRuleScreen<>(
                    this, menu.getMaintenanceEditorData()));
            return;
        }
        int max = Math.max(0, entries().size() - VISIBLE_ROWS);
        scrollbar.setRange(0, max, Math.max(1, VISIBLE_ROWS - 1));
        updateTabLabels();
        updateAddControls();
    }

    private void updateAddControls() {
        boolean visible = view == View.RESERVES;
        reserveAmount.setVisible(visible);
        addReserveButton.visible = visible;
        var markSlot = menu.getGlobalReserveMarkSlot();
        markSlot.setActive(visible);
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_GLOBAL_RESERVE_MARK, !visible);
        addReserveButton.active = visible
                && menu.maintenanceAvailable
                && markedReserveKey() != null
                && parsedAddAmount() != Long.MIN_VALUE;
    }

    private static boolean validAddAmountDraft(String value) {
        if (value.isEmpty() || value.equals("-1")) return true;
        try {
            return Long.parseLong(value) > 0L;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private long parsedAddAmount() {
        try {
            long value = Long.parseLong(reserveAmount.getValue());
            return value == -1L || value > 0L ? value : Long.MIN_VALUE;
        } catch (NumberFormatException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private AEKey markedReserveKey() {
        var marked = GenericStack.fromItemStack(menu.getGlobalReserveMarkSlot().getItem());
        return marked != null ? marked.what() : null;
    }

    private void addMarkedReserve() {
        var key = markedReserveKey();
        long value = parsedAddAmount();
        if (key == null || value == Long.MIN_VALUE || !menu.maintenanceAvailable) return;
        menu.sendGlobalReserve(key, value, ReservedStockMatchMode.EXACT);
        menu.getGlobalReserveMarkSlot().setFilterTo(
                net.minecraft.world.item.ItemStack.EMPTY);
        reserveAmount.setValue("1");
    }

    private List<OverviewEntry> entries() {
        String needle = search.getValue().strip().toLowerCase(Locale.ROOT);
        var summaries = menu.getMaintenanceSummary();
        var merged = new LinkedHashMap<AEKey, MaintenanceSummarySyncPacket.Entry>();

        if (view == View.RULES) {
            summaries.values().stream()
                    .filter(MaintenanceSummarySyncPacket.Entry::ruleConfigured)
                    .forEach(entry -> merged.put(entry.key(), entry));
        } else {
            summaries.values().stream()
                    .filter(entry -> entry.globalReserveConfigured() || !needle.isEmpty())
                    .forEach(entry -> merged.put(entry.key(), entry));

            // Searching the reserve view doubles as the explicit "add" flow. Reuse AE2's
            // synchronized terminal repository so no second imitation inventory list is built.
            if (!needle.isEmpty()) {
                for (var networkEntry : getParent().getNetworkEntriesForMaintenance()) {
                    var key = networkEntry.getWhat();
                    if (key == null || merged.containsKey(key)) continue;
                    merged.put(key, new MaintenanceSummarySyncPacket.Entry(
                            key, false, InventoryMaintenanceStatus.IDLE,
                            Math.max(0L, networkEntry.getStoredAmount()),
                            0L, 0L, 0L, 0L, ReservedStockMatchMode.EXACT,
                            false, networkEntry.isCraftable(), false));
                }
            }
        }

        var result = new ArrayList<OverviewEntry>();
        for (var entry : merged.values()) {
            String displayName = entry.key().getDisplayName().getString();
            if (!needle.isEmpty()
                    && !displayName.toLowerCase(Locale.ROOT).contains(needle)
                    && !entry.key().getId().toString().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            result.add(new OverviewEntry(entry, displayName));
        }
        result.sort(Comparator.comparing(OverviewEntry::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.summary().key().getId().toString()));
        return List.copyOf(result);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("ae2lt.tianshu.maintenance.overview_title"),
                10, 9, 0x30343B, false);
        graphics.drawString(font,
                Component.translatable("ae2lt.tianshu.maintenance.column.item"),
                CONTENT_TEXT_LEFT, 53, 0x5D646D, false);
        drawRightAligned(graphics,
                Component.translatable("ae2lt.tianshu.maintenance.column.stock"),
                STOCK_COLUMN_RIGHT, 53, 0x5D646D);
        drawRightAligned(graphics, Component.translatable(view == View.RULES
                        ? "ae2lt.tianshu.maintenance.column.target"
                        : "ae2lt.tianshu.maintenance.column.reserve"),
                VALUE_COLUMN_RIGHT, 53, 0x5D646D);
        graphics.fill(LIST_LEFT + 1, FIRST_ROW - 1, LIST_RIGHT - 1, FIRST_ROW,
                0x335D646D);

        int localMouseX = mouseX - leftPos;
        int localMouseY = mouseY - topPos;
        var entries = entries();
        int start = scrollbar.getCurrentScroll();
        int end = Math.min(entries.size(), start + VISIBLE_ROWS);
        for (int index = start; index < end; index++) {
            int row = index - start;
            int y = FIRST_ROW + row * ROW_HEIGHT;
            var summary = entries.get(index).summary();
            boolean hovered = localMouseX >= LIST_LEFT && localMouseX < LIST_RIGHT
                    && localMouseY >= y && localMouseY < y + ROW_HEIGHT - 1;
            graphics.fill(LIST_LEFT, y, LIST_RIGHT, y + ROW_HEIGHT - 1,
                    hovered ? 0x553B719F : (row & 1) == 0 ? 0x1EFFFFFF : 0x12000000);
            graphics.fill(12, y + 8, 16, y + 12, badgeColor(summary));
            graphics.renderItem(summary.key().wrapForDisplayOrFilter(), 18, y + 2);
            graphics.drawString(font,
                    font.plainSubstrByWidth(entries.get(index).displayName(), 70),
                    CONTENT_TEXT_LEFT, y + 6, summary.ruleConfigured() && !summary.craftable()
                            ? 0xA73535 : 0x30343B, false);
            drawRightAligned(graphics, compactAmount(summary.key(), summary.storedAmount()),
                    STOCK_COLUMN_RIGHT, y + 6, 0x3D4650);
            String finalValue = view == View.RULES
                    ? compactAmount(summary.key(), summary.upperThreshold())
                    : formatReserve(summary.globalReserve());
            drawRightAligned(graphics, finalValue, VALUE_COLUMN_RIGHT, y + 6,
                    view == View.RESERVES && summary.globalReserve() != 0L ? 0x245E91 : 0x3D4650);
        }

        if (!menu.maintenanceAvailable) {
            graphics.drawCenteredString(font,
                    Component.translatable("ae2lt.tianshu.maintenance.unavailable"),
                    LIST_CENTER_X, EMPTY_TEXT_Y, 0xA73535);
        } else if (entries.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable(view == View.RULES
                            ? "ae2lt.tianshu.maintenance.empty"
                            : search.getValue().isBlank()
                                    ? "ae2lt.tianshu.reserve.empty"
                                    : "ae2lt.tianshu.reserve.no_match"),
                    LIST_CENTER_X, EMPTY_TEXT_Y, 0x555B64);
        }

        if (view == View.RESERVES && menu.maintenanceAvailable) {
            graphics.drawString(font,
                    font.plainSubstrByWidth(Component.translatable(
                            "ae2lt.tianshu.reserve.add_hint").getString(), 188),
                    10, 190, 0x666D75, false);
        }
        if (menu.isMaintenanceSummaryOverflow()) {
            graphics.drawString(font,
                    Component.translatable("ae2lt.tianshu.maintenance.summary_too_large"),
                    10, 232, 0xA73535, false);
        }
    }

    private void drawRightAligned(GuiGraphics graphics, String text, int right, int y, int color) {
        graphics.drawString(font, text, right - font.width(text), y, color, false);
    }

    private void drawRightAligned(
            GuiGraphics graphics, Component text, int right, int y, int color) {
        graphics.drawString(font, text, right - font.width(text), y, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            int row = (int) ((mouseY - topPos - FIRST_ROW) / ROW_HEIGHT);
            var entries = entries();
            int index = scrollbar.getCurrentScroll() + row;
            if (mouseY >= topPos + FIRST_ROW
                    && mouseY < topPos + FIRST_ROW + VISIBLE_ROWS * ROW_HEIGHT
                    && row >= 0 && row < VISIBLE_ROWS && index >= 0 && index < entries.size()
                    && mouseX >= leftPos + LIST_LEFT && mouseX < leftPos + LIST_RIGHT) {
                var summary = entries.get(index).summary();
                if (view == View.RULES) {
                    requestRuleEditor(summary.key());
                } else {
                    switchToScreen(new GlobalReserveEditScreen<>(this, summary,
                            variantsFor(summary.key())));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void requestRuleEditor(AEKey key) {
        if (key == null || awaitingRuleEditor) return;
        requestedRuleEditorRevision = menu.getMaintenanceEditorRevision();
        awaitingRuleEditor = true;
        menu.requestMaintenanceEditor(key);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= leftPos + LIST_LEFT && mouseX < leftPos + LIST_RIGHT
                && mouseY >= topPos + FIRST_ROW
                && mouseY < topPos + FIRST_ROW + VISIBLE_ROWS * ROW_HEIGHT) {
            scrollbar.setCurrentScroll(scrollbar.getCurrentScroll() - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        int row = (y - topPos - FIRST_ROW) / ROW_HEIGHT;
        var entries = entries();
        int index = scrollbar.getCurrentScroll() + row;
        if (y >= topPos + FIRST_ROW && y < topPos + FIRST_ROW + VISIBLE_ROWS * ROW_HEIGHT
                && row >= 0 && row < VISIBLE_ROWS && index >= 0 && index < entries.size()
                && x >= leftPos + LIST_LEFT && x < leftPos + LIST_RIGHT) {
            var summary = entries.get(index).summary();
            var lines = new ArrayList<Component>();
            lines.add(summary.key().getDisplayName());
            lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.stock",
                    compactAmount(summary.key(), summary.storedAmount())).withStyle(ChatFormatting.GRAY));
            if (summary.ruleConfigured()) {
                lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.thresholds",
                        summary.lowerThreshold(), summary.upperThreshold())
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.batch",
                        summary.amountPerJob()).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("ae2lt.tianshu.maintenance.status."
                        + summary.status().name().toLowerCase(Locale.ROOT))
                        .withStyle(statusFormatting(summary.status())));
            }
            if (summary.globalReserve() != 0L) {
                lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.reserve",
                        formatReserve(summary.globalReserve()),
                        Component.translatable(summary.globalMode() == ReservedStockMatchMode.EXACT
                                ? "ae2lt.tianshu.reserve.exact"
                                : "ae2lt.tianshu.reserve.ignore_nbt"))
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
            lines.add(Component.translatable(view == View.RULES
                    ? "ae2lt.tianshu.maintenance.click_edit_rule"
                    : "ae2lt.tianshu.maintenance.click_edit_reserve")
                    .withStyle(ChatFormatting.DARK_GRAY));
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

    private static int badgeColor(MaintenanceSummarySyncPacket.Entry entry) {
        if (!entry.ruleConfigured()) return entry.globalReserve() == 0L ? 0xFF8A9199 : 0xFF3E7FB5;
        return switch (InventoryMaintenanceBadge.from(entry.status())) {
            case GREEN -> 0xFF35B956;
            case YELLOW -> 0xFFE1AD2B;
            case RED -> 0xFFD34A4A;
            case GRAY -> 0xFF858B91;
        };
    }

    private static ChatFormatting statusFormatting(InventoryMaintenanceStatus status) {
        return switch (InventoryMaintenanceBadge.from(status)) {
            case GREEN -> ChatFormatting.GREEN;
            case YELLOW -> ChatFormatting.GOLD;
            case RED -> ChatFormatting.RED;
            case GRAY -> ChatFormatting.GRAY;
        };
    }

    private static String compactAmount(AEKey key, long amount) {
        return key.formatAmount(Math.max(0L, amount), AmountFormat.SLOT);
    }

    private static String formatReserve(long amount) {
        return amount < 0L ? "∞" : Long.toString(amount);
    }

    private List<ReserveVariant> variantsFor(AEKey selected) {
        var identity = selected.dropSecondary();
        var variants = new LinkedHashMap<AEKey, ReserveVariant>();
        for (var networkEntry : getParent().getNetworkEntriesForMaintenance()) {
            var candidate = networkEntry.getWhat();
            if (candidate == null || !identity.equals(candidate.dropSecondary())) continue;
            var summary = menu.getMaintenanceSummaryEntry(candidate);
            boolean exactReserve = summary != null && summary.globalReserveConfigured()
                    && summary.globalMode() == ReservedStockMatchMode.EXACT;
            var variant = new ReserveVariant(candidate,
                    Math.max(0L, networkEntry.getStoredAmount()), networkEntry.isCraftable(), exactReserve);
            variants.merge(candidate, variant, (left, right) -> new ReserveVariant(
                    candidate, Math.max(left.storedAmount(), right.storedAmount()),
                    left.craftable() || right.craftable(),
                    left.exactReserveConfigured() || right.exactReserveConfigured()));
        }
        var selectedSummary = menu.getMaintenanceSummaryEntry(selected);
        variants.putIfAbsent(selected, new ReserveVariant(selected,
                selectedSummary != null ? selectedSummary.storedAmount() : 0L,
                selectedSummary != null && selectedSummary.craftable(),
                selectedSummary != null && selectedSummary.globalReserveConfigured()
                        && selectedSummary.globalMode() == ReservedStockMatchMode.EXACT));
        return variants.values().stream()
                .sorted(Comparator.comparing(
                                (ReserveVariant variant) -> variant.key().getDisplayName().getString(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(variant -> variant.key().getId().toString()))
                .toList();
    }

    private enum View {
        RULES("ae2lt.tianshu.maintenance.rules_tab"),
        RESERVES("ae2lt.tianshu.maintenance.reserves_tab");

        private final String translationKey;

        View(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private record OverviewEntry(MaintenanceSummarySyncPacket.Entry summary, String displayName) {
    }

    private record ReserveVariant(
            AEKey key, long storedAmount, boolean craftable, boolean exactReserveConfigured) {
    }

    private static final class GlobalReserveEditScreen<M extends TianshuPatternEncodingTermMenu>
            extends AESubScreen<M, TianshuGlobalReserveScreen<M>> {
        private static final int VARIANT_FIRST_ROW = 134;
        private static final int VARIANT_ROW_HEIGHT = 17;
        private static final int VISIBLE_VARIANTS = 3;

        private final MaintenanceSummarySyncPacket.Entry entry;
        private final List<ReserveVariant> variants;
        private final AETextField amount;
        private final Button modeButton;
        private final Button deleteButton;
        private final Button saveButton;
        private final Scrollbar scrollbar;
        private ReservedStockMatchMode mode;

        GlobalReserveEditScreen(
                TianshuGlobalReserveScreen<M> parent,
                MaintenanceSummarySyncPacket.Entry entry,
                List<ReserveVariant> variants) {
            this(parent, entry, variants,
                    new EditorState(entry.globalReserve(), entry.globalMode()));
        }

        private GlobalReserveEditScreen(
                TianshuGlobalReserveScreen<M> parent,
                MaintenanceSummarySyncPacket.Entry entry,
                List<ReserveVariant> variants,
                EditorState state) {
            super(parent, state.mode == ReservedStockMatchMode.IGNORE_SECONDARY
                    ? "/screens/tianshu_reserve_edit_expanded.json"
                    : "/screens/tianshu_reserve_edit.json");
            this.entry = entry;
            this.variants = List.copyOf(variants);
            this.mode = state.mode;

            scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.SMALL);
            scrollbar.setCaptureMouseWheel(false);
            amount = widgets.addTextField("amount");
            amount.setMaxLength(19);
            amount.setFilter(GlobalReserveEditScreen::validDraft);
            amount.setValue(Long.toString(state.amount));
            var scopeButton = widgets.addButton("scope",
                    Component.translatable("ae2lt.tianshu.reserve.global"), () -> { });
            scopeButton.active = false;
            modeButton = widgets.addButton("mode", modeLabel(), this::toggleMode);
            widgets.addButton("zero", Component.literal("0"), () -> amount.setValue("0"));
            widgets.addButton("stack", Component.literal("64"), () -> amount.setValue("64"));
            widgets.addButton("infinite", Component.literal("∞"), () -> amount.setValue("-1"));
            deleteButton = widgets.addButton("delete",
                    Component.translatable("ae2lt.tianshu.reserve.delete"), this::delete);
            saveButton = widgets.addButton("save", Component.translatable("gui.done"), this::save);
            widgets.addButton("cancel", Component.translatable("gui.cancel"), this::returnToParent);
            widgets.add("back", new TabButton(
                    Icon.ARROW_LEFT, Component.translatable("gui.back"), ignored -> returnToParent()));
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

        private void toggleMode() {
            long parsed = parsedAmount();
            if (parsed == Long.MIN_VALUE) return;
            mode = mode == ReservedStockMatchMode.EXACT
                    ? ReservedStockMatchMode.IGNORE_SECONDARY : ReservedStockMatchMode.EXACT;
            switchToScreen(new GlobalReserveEditScreen<>(
                    getParent(), entry, variants, new EditorState(parsed, mode)));
        }

        private Component modeLabel() {
            return Component.translatable(mode == ReservedStockMatchMode.EXACT
                    ? "ae2lt.tianshu.reserve.exact" : "ae2lt.tianshu.reserve.ignore_nbt");
        }

        private void save() {
            long parsed = parsedAmount();
            if (parsed == Long.MIN_VALUE) return;
            menu.sendGlobalReserve(entry.key(), parsed, mode);
            returnToParent();
        }

        private void delete() {
            menu.sendGlobalReserve(entry.key(), 0L, entry.globalMode());
            returnToParent();
        }

        @Override
        protected void updateBeforeRender() {
            super.updateBeforeRender();
            deleteButton.active = entry.globalReserveConfigured();
            saveButton.active = parsedAmount() != Long.MIN_VALUE;
            modeButton.setMessage(modeLabel());
            boolean showVariants = mode == ReservedStockMatchMode.IGNORE_SECONDARY
                    && !variants.isEmpty();
            scrollbar.setVisible(showVariants);
            scrollbar.setRange(0, Math.max(0, variants.size() - VISIBLE_VARIANTS), 1);
        }

        @Override
        public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
            super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
            graphics.drawString(font, Component.translatable("ae2lt.tianshu.reserve.title"),
                    10, 9, 0x30343B, false);
            graphics.renderItem(entry.key().wrapForDisplayOrFilter(), 16, 32);
            graphics.drawString(font,
                    font.plainSubstrByWidth(entry.key().getDisplayName().getString(), 147),
                    39, 35, 0x30343B, false);
            var currentProtected = Component.translatable(
                    "ae2lt.tianshu.reserve.current_protected",
                    compactAmount(entry.key(), visibleStock()),
                    compactAmount(entry.key(), protectedStock()));
            graphics.drawString(font,
                    font.plainSubstrByWidth(currentProtected.getString(), 187),
                    10, 83, 0x59616B, false);
            var amountLabel = Component.translatable("ae2lt.tianshu.reserve.amount");
            graphics.drawString(font, font.plainSubstrByWidth(amountLabel.getString(), 64),
                    10, 99, 0x40464E, false);
            if (mode == ReservedStockMatchMode.IGNORE_SECONDARY) {
                if (!variants.isEmpty()) {
                    graphics.drawString(font,
                            Component.translatable("ae2lt.tianshu.reserve.variant_title"),
                            10, 116, 0x555D66, false);
                    int start = scrollbar.getCurrentScroll();
                    int end = Math.min(variants.size(), start + VISIBLE_VARIANTS);
                    for (int index = start; index < end; index++) {
                        int y = VARIANT_FIRST_ROW + (index - start) * VARIANT_ROW_HEIGHT;
                        var variant = variants.get(index);
                        graphics.renderItem(variant.key().wrapForDisplayOrFilter(), 12, y);
                        graphics.drawString(font,
                                font.plainSubstrByWidth(
                                        variant.key().getDisplayName().getString(), 112),
                                33, y + 4, 0x3C434B, false);
                        String stock = compactAmount(variant.key(), variant.storedAmount());
                        graphics.drawString(font, stock, 185 - font.width(stock), y + 4,
                                variant.craftable() ? 0x2F6D3C : 0x5C636B, false);
                    }
                } else {
                    var emptyText = Component.translatable(
                            "ae2lt.tianshu.reserve.variant_empty");
                    graphics.drawString(font, emptyText,
                            98 - font.width(emptyText) / 2, 156, 0x5D646D, false);
                }
            }
        }

        private long visibleStock() {
            if (mode == ReservedStockMatchMode.EXACT) return entry.storedAmount();
            long total = 0L;
            for (var variant : variants) {
                if (variant.exactReserveConfigured()) continue;
                if (Long.MAX_VALUE - total < variant.storedAmount()) return Long.MAX_VALUE;
                total += variant.storedAmount();
            }
            return total;
        }

        private long protectedStock() {
            long stock = visibleStock();
            long configured = parsedAmount();
            if (configured == Long.MIN_VALUE || configured == 0L) return 0L;
            return configured < 0L ? stock : Math.min(stock, configured);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (mode == ReservedStockMatchMode.IGNORE_SECONDARY
                    && !variants.isEmpty()
                    && mouseY >= topPos + VARIANT_FIRST_ROW
                    && mouseY < topPos + VARIANT_FIRST_ROW + VISIBLE_VARIANTS * VARIANT_ROW_HEIGHT) {
                scrollbar.setCurrentScroll(scrollbar.getCurrentScroll() - (int) Math.signum(delta));
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        @Override
        protected void renderTooltip(GuiGraphics graphics, int x, int y) {
            int row = (y - topPos - VARIANT_FIRST_ROW) / VARIANT_ROW_HEIGHT;
            int index = scrollbar.getCurrentScroll() + row;
            if (mode == ReservedStockMatchMode.IGNORE_SECONDARY
                    && y >= topPos + VARIANT_FIRST_ROW
                    && y < topPos + VARIANT_FIRST_ROW + VISIBLE_VARIANTS * VARIANT_ROW_HEIGHT
                    && row >= 0 && row < VISIBLE_VARIANTS
                    && index >= 0 && index < variants.size()
                    && x >= leftPos + 9 && x < leftPos + 187) {
                var variant = variants.get(index);
                var lines = new ArrayList<>(AEKeyRendering.getTooltip(variant.key()));
                lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.stock",
                        compactAmount(variant.key(), variant.storedAmount()))
                        .withStyle(ChatFormatting.GRAY));
                if (variant.craftable()) {
                    lines.add(ButtonToolTips.Craftable.text().withStyle(ChatFormatting.GREEN));
                }
                if (variant.exactReserveConfigured()) {
                    lines.add(Component.translatable("ae2lt.tianshu.reserve.exact_override")
                            .withStyle(ChatFormatting.DARK_AQUA));
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

        private record EditorState(long amount, ReservedStockMatchMode mode) {
        }
    }
}
