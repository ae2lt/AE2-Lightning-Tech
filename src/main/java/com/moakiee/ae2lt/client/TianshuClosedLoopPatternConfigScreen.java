package com.moakiee.ae2lt.client;

import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopDraftStatus;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopResultPage;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Server-backed, paged editor for one closed-loop pattern draft. */
final class TianshuClosedLoopPatternConfigScreen<M extends TianshuPatternEncodingTermMenu>
        extends AESubScreen<M, TianshuPatternEncodingTermScreen<M>> {
    private static final int SLOT_X = 13;
    private static final int ROW_SLOT_Y_OFFSET = 4;
    private static final int HIDDEN_SLOT = -10000;
    private static final int NAME_X = 34;
    private static final int MEMBER_NAME_WIDTH = 45;
    private static final int RESULT_NAME_WIDTH = 130;
    private static final int MEMBER_AMOUNT_X = 96;
    private static final int MEMBER_AMOUNT_WIDTH = 36;
    private static final int MEMBER_AMOUNT_Y_OFFSET = 6;
    private static final int NUMBER_FIELD_HEIGHT = 12;
    private static final int MEMBER_UP_X = 134;
    private static final int MEMBER_DOWN_X = 150;
    private static final int ROLE_X = 111;
    private static final int ROLE_WIDTH = 54;
    private static final int FOOTER_TEXT_WIDTH = 168;
    private static final int SETTINGS_FIELD_X = 96;
    private static final int SETTINGS_FIELD_WIDTH = 69;
    private static final int SETTINGS_EXECUTION_ROW_Y = TianshuPatternConfigLayout.HEADER_HEIGHT + 32;
    private static final int SETTINGS_STORED_ROW_Y =
            SETTINGS_EXECUTION_ROW_Y + TianshuPatternConfigLayout.ROW_HEIGHT;

    private final Scrollbar scrollbar;
    private final List<AE2Button> pageButtons;
    private final AETextField[] memberAmounts = new AETextField[TianshuPatternConfigLayout.VISIBLE_ROWS];
    private final int[] memberRows = new int[TianshuPatternConfigLayout.VISIBLE_ROWS];
    private final ArrowButton[] memberUp = new ArrowButton[TianshuPatternConfigLayout.VISIBLE_ROWS];
    private final ArrowButton[] memberDown = new ArrowButton[TianshuPatternConfigLayout.VISIBLE_ROWS];
    private final Button[] outputRoles = new Button[TianshuPatternConfigLayout.VISIBLE_ROWS];
    private Page page = Page.MEMBERS;
    private AETextField executionMultiplier;
    private AETextField storedMultiplier;
    private ArrowButton previousCandidate;
    private ArrowButton nextCandidate;
    private boolean syncingFields;
    @Nullable private ClosedLoopResultPage.Kind requestedResultKind;
    private int requestedResultRevision = Integer.MIN_VALUE;
    private int requestedResultOffset = -1;

    TianshuClosedLoopPatternConfigScreen(TianshuPatternEncodingTermScreen<M> parent) {
        super(parent, "/screens/tianshu_closed_loop_pattern_config.json");
        imageWidth = TianshuPatternConfigLayout.GUI_WIDTH;
        imageHeight = TianshuPatternConfigLayout.GUI_HEIGHT;

        widgets.add("button_back", new TabButton(Icon.BACK,
                Component.translatable("gui.back"), ignored -> closeEditor()));
        pageButtons = List.of(
                widgets.addButton("page_members", pageLabel(Page.MEMBERS),
                        () -> setPage(Page.MEMBERS)),
                widgets.addButton("page_outputs", pageLabel(Page.OUTPUTS),
                        () -> setPage(Page.OUTPUTS)),
                widgets.addButton("page_inputs", pageLabel(Page.EXTERNAL_INPUTS),
                        () -> setPage(Page.EXTERNAL_INPUTS)),
                widgets.addButton("page_seeds", pageLabel(Page.SEEDS),
                        () -> setPage(Page.SEEDS)),
                widgets.addButton("page_settings", pageLabel(Page.SETTINGS),
                        () -> setPage(Page.SETTINGS)));
        scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.SMALL);
    }

    @Override
    protected void init() {
        super.init();
        scrollbar.setHeight(TianshuPatternConfigLayout.SCROLLBAR_HEIGHT);
        for (int row = 0; row < TianshuPatternConfigLayout.VISIBLE_ROWS; row++) {
            final int visibleRow = row;
            int rowY = TianshuPatternConfigLayout.HEADER_HEIGHT
                    + row * TianshuPatternConfigLayout.ROW_HEIGHT;
            var amount = numberField(MEMBER_AMOUNT_X, rowY + MEMBER_AMOUNT_Y_OFFSET,
                    MEMBER_AMOUNT_WIDTH);
            amount.setMaxLength(19);
            amount.setFilter(TianshuClosedLoopPatternConfigScreen::isPositiveLongDraft);
            amount.setResponder(value -> submitMemberAmount(visibleRow, value));
            memberAmounts[row] = amount;

            memberUp[row] = addRenderableWidget(new ArrowButton(
                    Icon.ARROW_UP,
                    Component.translatable("ae2lt.tianshu.closed_loop.move_up"),
                    ignored -> moveMember(visibleRow, -1)));
            memberUp[row].setPosition(leftPos + MEMBER_UP_X, topPos + rowY + 4);
            memberDown[row] = addRenderableWidget(new ArrowButton(
                    Icon.ARROW_DOWN,
                    Component.translatable("ae2lt.tianshu.closed_loop.move_down"),
                    ignored -> moveMember(visibleRow, 1)));
            memberDown[row].setPosition(leftPos + MEMBER_DOWN_X, topPos + rowY + 4);

            var role = new AE2Button(leftPos + ROLE_X, topPos + rowY + 4, ROLE_WIDTH, 16,
                    Component.empty(), ignored -> {
                    });
            outputRoles[row] = addRenderableWidget(role);
        }

        executionMultiplier = numberBox(SETTINGS_FIELD_X, SETTINGS_EXECUTION_ROW_Y,
                menu.closedLoopExecutionSeedMultiplier);
        storedMultiplier = numberBox(SETTINGS_FIELD_X, SETTINGS_STORED_ROW_Y,
                menu.closedLoopStoredTaskMultiplier);
        executionMultiplier.setResponder(ignored -> submitMultipliers());
        storedMultiplier.setResponder(ignored -> submitMultipliers());
        executionMultiplier.setTooltipMessage(List.of(Component.translatable(
                "ae2lt.tianshu.terminal.closed_loop.execution_seed_multiplier.tooltip")));
        storedMultiplier.setTooltipMessage(List.of(Component.translatable(
                "ae2lt.tianshu.terminal.closed_loop.stored_task_multiplier.tooltip")));

        previousCandidate = addRenderableWidget(new ArrowButton(
                Icon.ARROW_LEFT,
                Component.translatable("ae2lt.tianshu.closed_loop.previous_candidate"),
                ignored -> menu.selectClosedLoopCandidate(-1)));
        previousCandidate.setPosition(leftPos + 116, topPos + TianshuPatternConfigLayout.HEADER_HEIGHT + 4);
        nextCandidate = addRenderableWidget(new ArrowButton(
                Icon.ARROW_RIGHT,
                Component.translatable("ae2lt.tianshu.closed_loop.next_candidate"),
                ignored -> menu.selectClosedLoopCandidate(1)));
        nextCandidate.setPosition(leftPos + 145, topPos + TianshuPatternConfigLayout.HEADER_HEIGHT + 4);

        hideParentSlots();
        configurePage();
    }

    private AETextField numberBox(int x, int y, int value) {
        var field = numberField(x, y, SETTINGS_FIELD_WIDTH);
        field.setMaxLength(9);
        field.setFilter(TianshuClosedLoopPatternConfigScreen::isPositiveIntDraft);
        syncingFields = true;
        field.setValue(Integer.toString(value));
        syncingFields = false;
        return field;
    }

    private AETextField numberField(int x, int y, int width) {
        var field = new AETextField(style, font, leftPos + x, topPos + y,
                width, NUMBER_FIELD_HEIGHT);
        field.setBordered(false);
        return addRenderableWidget(field);
    }

    private void setPage(Page newPage) {
        if (page == newPage) return;
        page = newPage;
        scrollbar.setCurrentScroll(0);
        configurePage();
    }

    private void configurePage() {
        int rows = switch (page) {
            case MEMBERS -> TianshuPatternEncodingTermMenu.CLOSED_LOOP_MEMBER_SLOTS;
            case OUTPUTS -> TianshuPatternEncodingTermMenu.CLOSED_LOOP_OUTPUT_SLOTS;
            case EXTERNAL_INPUTS -> Math.min(menu.closedLoopExternalInputCount,
                    TianshuPatternEncodingTermMenu.CLOSED_LOOP_RESULT_SLOTS);
            case SEEDS -> Math.min(menu.closedLoopSeedInputCount,
                    TianshuPatternEncodingTermMenu.CLOSED_LOOP_RESULT_SLOTS);
            case SETTINGS -> 0;
        };
        scrollbar.setRange(0,
                Math.max(0, rows - TianshuPatternConfigLayout.VISIBLE_ROWS), 1);
        scrollbar.setVisible(page != Page.SETTINGS && rows > TianshuPatternConfigLayout.VISIBLE_ROWS);
        for (int i = 0; i < pageButtons.size(); i++) {
            var value = Page.values()[i];
            pageButtons.get(i).setMessage(pageLabel(value).copy().withStyle(
                    value == page ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        }
        updateVisibleSlots();
        updateControls();
        requestVisibleResultPage();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        updateVisibleSlots();
        updateControls();
        requestVisibleResultPage();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        syncFields();
    }

    private void updateVisibleSlots() {
        hideSlots(menu.getClosedLoopMemberSlots());
        hideSlots(menu.getClosedLoopOutputSlots());
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_MEMBER, page != Page.MEMBERS);
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_OUTPUT_MARK, page != Page.OUTPUTS);

        var slots = switch (page) {
            case MEMBERS -> menu.getClosedLoopMemberSlots();
            case OUTPUTS -> menu.getClosedLoopOutputSlots();
            case EXTERNAL_INPUTS, SEEDS, SETTINGS -> List.<AppEngSlot>of();
        };
        int scroll = scrollbar.getCurrentScroll();
        for (int visible = 0; visible < TianshuPatternConfigLayout.VISIBLE_ROWS; visible++) {
            int index = scroll + visible;
            if (index >= slots.size()) break;
            var slot = slots.get(index);
            slot.x = SLOT_X;
            slot.y = TianshuPatternConfigLayout.HEADER_HEIGHT
                    + visible * TianshuPatternConfigLayout.ROW_HEIGHT + ROW_SLOT_Y_OFFSET;
            slot.setActive(true);
        }
    }

    private void updateControls() {
        int scroll = scrollbar.getCurrentScroll();
        for (int visible = 0; visible < TianshuPatternConfigLayout.VISIBLE_ROWS; visible++) {
            int memberIndex = scroll + visible;
            memberRows[visible] = memberIndex;
            boolean memberPage = page == Page.MEMBERS
                    && memberIndex < TianshuPatternEncodingTermMenu.CLOSED_LOOP_MEMBER_SLOTS;
            var memberSlot = memberPage ? menu.getClosedLoopMemberSlots().get(memberIndex) : null;
            boolean memberPresent = memberSlot != null && !memberSlot.getItem().isEmpty();
            memberAmounts[visible].setVisible(memberPage);
            memberAmounts[visible].active = memberPresent;
            memberAmounts[visible].setEditable(memberPresent);
            memberUp[visible].visible = memberPage;
            memberDown[visible].visible = memberPage;
            memberUp[visible].active = memberPresent && memberIndex > 0;
            memberDown[visible].active = memberPresent
                    && memberIndex + 1 < TianshuPatternEncodingTermMenu.CLOSED_LOOP_MEMBER_SLOTS;

            int outputIndex = scroll + visible;
            boolean outputPage = page == Page.OUTPUTS
                    && outputIndex < TianshuPatternEncodingTermMenu.CLOSED_LOOP_OUTPUT_SLOTS;
            boolean outputPresent = outputPage
                    && !menu.getClosedLoopOutputSlots().get(outputIndex).getItem().isEmpty();
            outputRoles[visible].visible = outputPresent;
            outputRoles[visible].active = false;
            if (outputPage) outputRoles[visible].setMessage(roleLabel(
                    menu.closedLoopDraftSync.outputRole(outputIndex)));
        }
        boolean settings = page == Page.SETTINGS;
        executionMultiplier.setVisible(settings);
        storedMultiplier.setVisible(settings);
        previousCandidate.visible = settings;
        nextCandidate.visible = settings;
        previousCandidate.active = menu.closedLoopCandidateCount > 1;
        nextCandidate.active = menu.closedLoopCandidateCount > 1;
        syncFields();
    }

    private void syncFields() {
        syncingFields = true;
        try {
            if (page == Page.MEMBERS) {
                for (int visible = 0; visible < memberAmounts.length; visible++) {
                    var field = memberAmounts[visible];
                    int index = memberRows[visible];
                    if (field.isFocused() || index < 0
                            || index >= TianshuPatternEncodingTermMenu.CLOSED_LOOP_MEMBER_SLOTS) continue;
                    boolean present = !menu.getClosedLoopMemberSlots().get(index).getItem().isEmpty();
                    String value = present
                            ? Long.toString(Math.max(1L, menu.closedLoopDraftSync.copies(index))) : "";
                    if (!field.getValue().equals(value)) field.setValue(value);
                }
            }
            if (executionMultiplier != null && !executionMultiplier.isFocused()) {
                String value = Integer.toString(menu.closedLoopExecutionSeedMultiplier);
                if (!executionMultiplier.getValue().equals(value)) executionMultiplier.setValue(value);
            }
            if (storedMultiplier != null && !storedMultiplier.isFocused()) {
                String value = Integer.toString(menu.closedLoopStoredTaskMultiplier);
                if (!storedMultiplier.getValue().equals(value)) storedMultiplier.setValue(value);
            }
        } finally {
            syncingFields = false;
        }
    }

    private void submitMemberAmount(int visibleRow, String value) {
        if (syncingFields || page != Page.MEMBERS || value.isEmpty()) return;
        try {
            long parsed = Long.parseLong(value);
            if (parsed >= 1L) menu.setClosedLoopMemberCopies(memberRows[visibleRow], parsed);
        } catch (NumberFormatException ignored) {
        }
    }

    private void moveMember(int visibleRow, int direction) {
        if (page == Page.MEMBERS) menu.moveClosedLoopMember(memberRows[visibleRow], direction);
    }

    private void submitMultipliers() {
        if (syncingFields || executionMultiplier == null || storedMultiplier == null
                || executionMultiplier.getValue().isEmpty() || storedMultiplier.getValue().isEmpty()) return;
        try {
            int execution = Integer.parseInt(executionMultiplier.getValue());
            int stored = Integer.parseInt(storedMultiplier.getValue());
            if (execution >= 1 && stored >= 1) menu.setClosedLoopMultipliers(execution, stored);
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        switch (page) {
            case MEMBERS -> drawMemberRows(graphics);
            case OUTPUTS -> drawOutputRows(
                    graphics, menu.getClosedLoopOutputSlots(), ROLE_X - NAME_X - 4);
            case EXTERNAL_INPUTS -> drawResultRows(
                    graphics, ClosedLoopResultPage.Kind.EXTERNAL_INPUTS);
            case SEEDS -> drawResultRows(graphics, ClosedLoopResultPage.Kind.SEEDS);
            case SETTINGS -> drawSettings(graphics);
        }
        var status = Component.translatable("ae2lt.tianshu.closed_loop.status."
                + menu.closedLoopDraftStatus.name().toLowerCase(Locale.ROOT));
        int color = statusColor(menu.closedLoopDraftStatus);
        graphics.drawString(font, font.plainSubstrByWidth(status.getString(), FOOTER_TEXT_WIDTH),
                8, 161, color, false);
        if (page != Page.SETTINGS) {
            int rows = currentRowCount();
            graphics.drawString(font,
                    Component.translatable("ae2lt.tianshu.closed_loop.page_count",
                            rows == 0 ? 0 : scrollbar.getCurrentScroll() + 1, rows),
                    8, 173, 0x666666, false);
        }
    }

    private void drawMemberRows(GuiGraphics graphics) {
        int scroll = scrollbar.getCurrentScroll();
        for (int visible = 0; visible < TianshuPatternConfigLayout.VISIBLE_ROWS; visible++) {
            int index = scroll + visible;
            if (index >= menu.getClosedLoopMemberSlots().size()) break;
            int y = TianshuPatternConfigLayout.HEADER_HEIGHT
                    + visible * TianshuPatternConfigLayout.ROW_HEIGHT + 8;
            var stack = menu.getClosedLoopMemberSlots().get(index).getItem();
            graphics.drawString(font, Integer.toString(index + 1), NAME_X, y, 0x777777, false);
            if (!stack.isEmpty()) {
                graphics.drawString(font,
                        font.plainSubstrByWidth(stack.getHoverName().getString(), MEMBER_NAME_WIDTH),
                        NAME_X + 13, y, 0x404040, false);
            }
        }
    }

    private void drawOutputRows(GuiGraphics graphics, List<AppEngSlot> slots, int width) {
        int scroll = scrollbar.getCurrentScroll();
        for (int visible = 0; visible < TianshuPatternConfigLayout.VISIBLE_ROWS; visible++) {
            int index = scroll + visible;
            if (index >= slots.size()) break;
            var stack = slots.get(index).getItem();
            if (stack.isEmpty()) continue;
            int y = TianshuPatternConfigLayout.HEADER_HEIGHT
                    + visible * TianshuPatternConfigLayout.ROW_HEIGHT + 8;
            var generic = GenericStack.fromItemStack(stack);
            var name = generic != null && generic.what() != null
                    ? generic.what().getDisplayName().getString() : stack.getHoverName().getString();
            graphics.drawString(font, font.plainSubstrByWidth(name, width),
                    NAME_X, y, 0x404040, false);
        }
    }

    private void drawResultRows(GuiGraphics graphics, ClosedLoopResultPage.Kind kind) {
        var resultPage = menu.getClosedLoopResultPage(kind, scrollbar.getCurrentScroll());
        if (resultPage == null) return;
        for (int visible = 0; visible < resultPage.entries().size(); visible++) {
            var entry = resultPage.entries().get(visible);
            int rowY = TianshuPatternConfigLayout.HEADER_HEIGHT
                    + visible * TianshuPatternConfigLayout.ROW_HEIGHT;
            graphics.renderItem(GenericStack.wrapInItemStack(entry), SLOT_X, rowY + ROW_SLOT_Y_OFFSET);
            graphics.drawString(font,
                    font.plainSubstrByWidth(entry.what().getDisplayName().getString(), RESULT_NAME_WIDTH),
                    NAME_X, rowY + 8, 0x404040, false);
            var poseStack = graphics.pose();
            poseStack.pushPose();
            poseStack.translate(0, 0, 100);
            StackSizeRenderer.renderSizeLabel(graphics, font, SLOT_X, rowY + ROW_SLOT_Y_OFFSET,
                    entry.what().formatAmount(entry.amount(), AmountFormat.SLOT), false);
            poseStack.popPose();
        }
    }

    private void requestVisibleResultPage() {
        ClosedLoopResultPage.Kind kind = switch (page) {
            case EXTERNAL_INPUTS -> ClosedLoopResultPage.Kind.EXTERNAL_INPUTS;
            case SEEDS -> ClosedLoopResultPage.Kind.SEEDS;
            default -> null;
        };
        if (kind == null) return;
        int offset = scrollbar.getCurrentScroll();
        if (menu.getClosedLoopResultPage(kind, offset) != null) return;
        if (requestedResultKind == kind
                && requestedResultRevision == menu.closedLoopResultRevision
                && requestedResultOffset == offset) return;
        requestedResultKind = kind;
        requestedResultRevision = menu.closedLoopResultRevision;
        requestedResultOffset = offset;
        menu.requestClosedLoopResultPage(kind, offset);
    }

    private void drawSettings(GuiGraphics graphics) {
        int top = TianshuPatternConfigLayout.HEADER_HEIGHT;
        graphics.drawString(font,
                Component.translatable("ae2lt.tianshu.closed_loop.candidate",
                        menu.closedLoopCandidateCount == 0 ? 0 : menu.closedLoopCandidateIndex + 1,
                        menu.closedLoopCandidateCount),
                12, top + 8, 0x404040, false);
        graphics.drawString(font,
                Component.translatable("ae2lt.tianshu.closed_loop.execution_multiplier"),
                12, SETTINGS_EXECUTION_ROW_Y + 2, 0x404040, false);
        graphics.drawString(font,
                Component.translatable("ae2lt.tianshu.closed_loop.stored_multiplier"),
                12, SETTINGS_STORED_ROW_Y + 2, 0x404040, false);
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY,
                       int mouseX, int mouseY, float partialTicks) {
        TianshuPatternConfigLayout.drawBackground(graphics, offsetX, offsetY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY != 0 && page != Page.SETTINGS) {
            scrollbar.setCurrentScroll(scrollbar.getCurrentScroll() + (deltaY > 0 ? -1 : 1));
            updateVisibleSlots();
            updateControls();
            requestVisibleResultPage();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeEditor();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        closeEditor();
    }

    private void closeEditor() {
        hideSlots(menu.getClosedLoopMemberSlots());
        hideSlots(menu.getClosedLoopOutputSlots());
        returnToParent();
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        ClosedLoopResultPage.Kind kind = switch (page) {
            case EXTERNAL_INPUTS -> ClosedLoopResultPage.Kind.EXTERNAL_INPUTS;
            case SEEDS -> ClosedLoopResultPage.Kind.SEEDS;
            default -> null;
        };
        int localX = x - leftPos;
        int localY = y - topPos;
        int resultTop = TianshuPatternConfigLayout.HEADER_HEIGHT + ROW_SLOT_Y_OFFSET;
        int visible = (localY - resultTop) / TianshuPatternConfigLayout.ROW_HEIGHT;
        if (kind != null && localX >= SLOT_X && localX < SLOT_X + 16
                && localY >= resultTop
                && visible >= 0 && visible < TianshuPatternConfigLayout.VISIBLE_ROWS) {
            var resultPage = menu.getClosedLoopResultPage(kind, scrollbar.getCurrentScroll());
            if (resultPage != null && visible < resultPage.entries().size()
                    && localY < resultTop + visible * TianshuPatternConfigLayout.ROW_HEIGHT + 16) {
                graphics.renderTooltip(font,
                        GenericStack.wrapInItemStack(resultPage.entries().get(visible)), x, y);
                return;
            }
        }
        super.renderTooltip(graphics, x, y);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        int index = hoveredSlot == null ? -1 : menu.getClosedLoopMemberSlots().indexOf(hoveredSlot);
        if (index < 0 || stack.isEmpty()) return lines;
        TianshuClosedLoopEncodingPanel.appendMemberTooltip(lines, menu, index, stack, minecraft.level);
        return lines;
    }

    private void hideParentSlots() {
        for (var semantic : List.of(
                SlotSemantics.CRAFTING_GRID,
                SlotSemantics.CRAFTING_RESULT,
                SlotSemantics.PROCESSING_INPUTS,
                SlotSemantics.PROCESSING_OUTPUTS,
                SlotSemantics.SMITHING_TABLE_TEMPLATE,
                SlotSemantics.SMITHING_TABLE_BASE,
                SlotSemantics.SMITHING_TABLE_ADDITION,
                SlotSemantics.SMITHING_TABLE_RESULT,
                SlotSemantics.STONECUTTING_INPUT,
                SlotSemantics.BLANK_PATTERN,
                SlotSemantics.ENCODED_PATTERN,
                SlotSemantics.PLAYER_INVENTORY,
                SlotSemantics.PLAYER_HOTBAR,
                SlotSemantics.TOOLBOX)) {
            setSlotsHidden(semantic, true);
        }
    }

    private static void hideSlots(List<? extends AppEngSlot> slots) {
        for (var slot : slots) {
            slot.setActive(false);
            slot.x = HIDDEN_SLOT;
            slot.y = HIDDEN_SLOT;
        }
    }

    private int currentRowCount() {
        return switch (page) {
            case MEMBERS -> TianshuPatternEncodingTermMenu.CLOSED_LOOP_MEMBER_SLOTS;
            case OUTPUTS -> TianshuPatternEncodingTermMenu.CLOSED_LOOP_OUTPUT_SLOTS;
            case EXTERNAL_INPUTS -> Math.min(menu.closedLoopExternalInputCount,
                    TianshuPatternEncodingTermMenu.CLOSED_LOOP_RESULT_SLOTS);
            case SEEDS -> Math.min(menu.closedLoopSeedInputCount,
                    TianshuPatternEncodingTermMenu.CLOSED_LOOP_RESULT_SLOTS);
            case SETTINGS -> 1;
        };
    }

    private static boolean isPositiveLongDraft(String value) {
        if (value.isEmpty()) return true;
        try {
            return Long.parseLong(value) >= 1L;
        } catch (NumberFormatException ignored) {
            return false;
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

    private static Component pageLabel(Page page) {
        return Component.translatable("ae2lt.tianshu.closed_loop.page."
                + page.name().toLowerCase(Locale.ROOT));
    }

    private static Component roleLabel(int role) {
        return switch (role) {
            case 1 -> Component.translatable("ae2lt.tianshu.closed_loop.role.primary")
                    .withStyle(ChatFormatting.GREEN);
            case 2 -> Component.translatable("ae2lt.tianshu.closed_loop.role.secondary");
            default -> Component.translatable("ae2lt.tianshu.closed_loop.role.none")
                    .withStyle(ChatFormatting.GRAY);
        };
    }

    private static int statusColor(ClosedLoopDraftStatus status) {
        return switch (status) {
            case VALID, ENCODED -> 0x228822;
            case EMPTY, NO_CANDIDATE -> 0x666666;
            case MISSING_PRIMARY_OUTPUT -> 0xAA7700;
            default -> 0xAA3333;
        };
    }

    private enum Page {
        MEMBERS,
        OUTPUTS,
        EXTERNAL_INPUTS,
        SEEDS,
        SETTINGS
    }

    private static final class ArrowButton extends IconButton {
        private final Icon icon;
        private final Component tooltip;

        private ArrowButton(Icon icon, Component tooltip, Button.OnPress onPress) {
            super(onPress);
            this.icon = icon;
            this.tooltip = tooltip;
        }

        @Override
        protected Icon getIcon() {
            return icon;
        }

        @Override
        public List<Component> getTooltipMessage() {
            return List.of(tooltip);
        }
    }
}
