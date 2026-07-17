package com.moakiee.ae2lt.client;

import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.client.gui.widgets.TabButton;
import appeng.client.gui.widgets.TabButton.Style;
import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.EmptyingAction;
import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.config.ViewItems;
import appeng.api.stacks.GenericStack;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.core.network.ServerboundPacket;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantics;
import appeng.parts.encoding.EncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternUploadRouting;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.mixin.client.AEBaseScreenAccessor;
import com.moakiee.ae2lt.mixin.client.VerticalButtonBarAccessor;
import com.moakiee.ae2lt.registry.ModItems;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.core.Direction;
import com.moakiee.thunderbolt.ae2.overload.model.MatchMode;
import appeng.client.gui.me.common.RepoSlot;
import org.lwjgl.glfw.GLFW;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceBadge;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import com.moakiee.ae2lt.logic.AdvancedAECompat;

public class TianshuPatternEncodingTermScreen<M extends TianshuPatternEncodingTermMenu>
        extends MEStorageScreen<M> {
    private final Map<EncodingMode, TianshuEncodingModePanel> modePanels =
            new EnumMap<>(EncodingMode.class);
    private final Map<TianshuEncodingMode, TabButton> modeTabs =
            new EnumMap<>(TianshuEncodingMode.class);
    private final DerivedModePanel derivedPanel = new DerivedModePanel();
    private final List<AE2Button> processingModeButtons;
    private final List<AE2Button> closedLoopModeButtons;
    private final AE2Button upload;
    private final AE2Button tianshuTarget;
    private final AE2Button globalReserve;
    private boolean awaitingMaintenanceEditor;
    private int requestedMaintenanceRevision;
    private int observedTianshuSelectionRevision = Integer.MIN_VALUE;

    public TianshuPatternEncodingTermScreen(
            M menu,
            Inventory inventory,
            Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);

        for (var mode : EncodingMode.values()) {
            var panel = switch (mode) {
                case CRAFTING -> new TianshuCraftingEncodingPanel(this, widgets);
                case PROCESSING -> new TianshuProcessingEncodingPanel(this, widgets);
                case SMITHING_TABLE -> new TianshuSmithingTableEncodingPanel(this, widgets);
                case STONECUTTING -> new TianshuStonecuttingEncodingPanel(this, widgets);
            };
            var tabButton = new TabButton(
                    panel.getIcon(),
                    panel.getTabTooltip(),
                    button -> menu.setMode(mode));
            tabButton.setStyle(Style.HORIZONTAL);

            var modeIndex = modeTabs.size();
            widgets.add("modePanel" + modeIndex, panel);
            widgets.add("modeTabButton" + modeIndex, tabButton);
            modeTabs.put(TianshuEncodingMode.fromAe2(mode), tabButton);
            modePanels.put(mode, panel);
        }

        widgets.add("encodePattern", new ActionButton(ActionItems.ENCODE, action -> menu.encode()));

        addExtraTab(TianshuEncodingMode.ADVANCED, ModItems.OVERLOAD_PATTERN.get().getDefaultInstance(),
                Component.translatable("ae2lt.tianshu.terminal.mode.advanced"), "modeTabButton4");
        addExtraTab(TianshuEncodingMode.OVERLOAD, ModItems.OVERLOAD_PATTERN.get().getDefaultInstance(),
                Component.translatable("ae2lt.tianshu.terminal.mode.overload"), "modeTabButton5");
        addExtraTab(TianshuEncodingMode.CLOSED_LOOP, ModItems.CLOSED_LOOP_PATTERN.get().getDefaultInstance(),
                Component.translatable("ae2lt.tianshu.terminal.mode.closed_loop"), "modeTabButton6");
        widgets.add("derivedModePanel", derivedPanel);

        processingModeButtons = List.of(
                widgets.addButton("processingMultiply2", Component.literal("×2"),
                        () -> menu.multiplyProcessing(hasShiftDown() ? 4 : 2)),
                widgets.addButton("processingMultiply5", Component.literal("×5"),
                        () -> menu.multiplyProcessing(hasShiftDown() ? 10 : 5)),
                widgets.addButton("processingDivide2", Component.literal("÷2"),
                        () -> menu.multiplyProcessing(hasShiftDown() ? -4 : -2)),
                widgets.addButton("processingDivide5", Component.literal("÷5"),
                        () -> menu.multiplyProcessing(hasShiftDown() ? -10 : -5)));
        var previousCandidate = widgets.addButton("closedLoopPrevious", Component.literal("<"),
                () -> menu.selectClosedLoopCandidate(-1));
        var nextCandidate = widgets.addButton("closedLoopNext", Component.literal(">"),
                () -> menu.selectClosedLoopCandidate(1));
        var executionSeedDown = widgets.addButton("closedLoopExecutionSeedDown", Component.literal("−"),
                () -> menu.changeClosedLoopExecutionSeedMultiplier(hasShiftDown() ? -10 : -1));
        var executionSeedUp = widgets.addButton("closedLoopExecutionSeedUp", Component.literal("+"),
                () -> menu.changeClosedLoopExecutionSeedMultiplier(hasShiftDown() ? 10 : 1));
        var storedTaskDown = widgets.addButton("closedLoopStoredTaskDown", Component.literal("−"),
                () -> menu.changeClosedLoopStoredTaskMultiplier(hasShiftDown() ? -10 : -1));
        var storedTaskUp = widgets.addButton("closedLoopStoredTaskUp", Component.literal("+"),
                () -> menu.changeClosedLoopStoredTaskMultiplier(hasShiftDown() ? 10 : 1));
        closedLoopModeButtons = List.of(
                previousCandidate, nextCandidate,
                executionSeedDown, executionSeedUp,
                storedTaskDown, storedTaskUp);
        var executionSeedTooltip = net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable(
                        "ae2lt.tianshu.terminal.closed_loop.execution_seed_multiplier.tooltip"));
        executionSeedDown.setTooltip(executionSeedTooltip);
        executionSeedUp.setTooltip(executionSeedTooltip);
        var storedTaskTooltip = net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable(
                        "ae2lt.tianshu.terminal.closed_loop.stored_task_multiplier.tooltip"));
        storedTaskDown.setTooltip(storedTaskTooltip);
        storedTaskUp.setTooltip(storedTaskTooltip);
        upload = widgets.addButton("tianshuUpload", Component.translatable("ae2lt.tianshu.terminal.upload"),
                this::openUploadScreen);
        tianshuTarget = widgets.addButton("tianshuTarget", Component.empty(),
                () -> menu.cycleTianshuTarget(hasShiftDown() ? -1 : 1));
        replaceViewModeButton();
        globalReserve = widgets.addButton("globalReserve",
                Component.translatable("ae2lt.tianshu.reserve.button"),
                () -> switchToScreen(new TianshuGlobalReserveScreen<>(this)));
    }

    private void addExtraTab(
            TianshuEncodingMode mode, net.minecraft.world.item.ItemStack icon,
            Component tooltip, String widgetId) {
        var tab = new TabButton(icon, tooltip, button -> menu.setTianshuMode(mode));
        tab.setStyle(Style.HORIZONTAL);
        widgets.add(widgetId, tab);
        modeTabs.put(mode, tab);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        var selected = menu.tianshuMode;
        for (var mode : EncodingMode.values()) {
            var modeSelected = selected.ae2Mode() == mode;
            modePanels.get(mode).setVisible(modeSelected);
        }
        modeTabs.forEach((mode, button) -> button.setSelected(mode == selected));
        if (observedTianshuSelectionRevision != menu.tianshuSelectionRevision) {
            observedTianshuSelectionRevision = menu.tianshuSelectionRevision;
            awaitingMaintenanceEditor = false;
            menu.resetClientTianshuScopedState();
        }
        if (menu.consumeTriggeredUpload()) {
            openUploadScreen();
            return;
        }
        if (awaitingMaintenanceEditor
                && menu.getMaintenanceEditorRevision() != requestedMaintenanceRevision
                && menu.getMaintenanceEditorData() != null) {
            awaitingMaintenanceEditor = false;
            switchToScreen(new TianshuMaintenanceRuleScreen<>(this, menu.getMaintenanceEditorData()));
            return;
        }
        boolean derived = !selected.isAe2Mode();
        derivedPanel.mode = derived ? selected : null;
        var advancedTab = modeTabs.get(TianshuEncodingMode.ADVANCED);
        if (advancedTab != null) advancedTab.visible = AdvancedAECompat.isLoaded();
        boolean processing = selected == TianshuEncodingMode.PROCESSING;
        processingModeButtons.forEach(button -> button.visible = processing);
        boolean closedLoop = selected == TianshuEncodingMode.CLOSED_LOOP;
        closedLoopModeButtons.forEach(button -> button.visible = closedLoop);
        upload.visible = true;
        upload.active = closedLoop
                ? menu.encodedClosedLoop && !menu.isTianshuSelectionPending()
                : hasEncodedPattern();
        tianshuTarget.setMessage(Component.translatable(
                "ae2lt.tianshu.terminal.target.short",
                menu.selectedTianshuIndex >= 0 ? menu.selectedTianshuIndex + 1 : "-",
                menu.availableTianshuCount));
        tianshuTarget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable(
                        menu.selectedTianshuMachine.isEmpty()
                                ? "ae2lt.tianshu.terminal.target.none"
                                : "ae2lt.tianshu.terminal.target.tooltip",
                        menu.selectedTianshuMachine, menu.selectedTianshuLocation)));
        tianshuTarget.active = !menu.isTianshuSelectionPending()
                && (menu.availableTianshuCount > 1
                || menu.selectedTianshuIndex < 0 && menu.availableTianshuCount > 0);
        globalReserve.active = menu.maintenanceAvailable && !menu.isTianshuSelectionPending();
    }

    private void openUploadScreen() {
        var stack = menu.getSlots(SlotSemantics.ENCODED_PATTERN).stream()
                .map(Slot::getItem).filter(item -> !item.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) return;
        var route = minecraft.level != null
                ? TianshuPatternUploadRouting.classify(stack, minecraft.level)
                : TianshuPatternUploadRouting.Route.INVALID;
        switch (route) {
            case CLOSED_LOOP_STORAGE -> menu.uploadTianshuPattern();
            case CRAFTING_ASSEMBLER -> menu.uploadTianshuCraftingPattern();
            case PROCESSING_PROVIDER -> switchToScreen(new TianshuUploadTargetScreen<>(this));
            case INVALID -> { }
        }
    }

    private boolean hasEncodedPattern() {
        return menu.getSlots(SlotSemantics.ENCODED_PATTERN).stream()
                .anyMatch(slot -> !slot.getItem().isEmpty());
    }

    private TianshuViewModeButton replaceViewModeButton() {
        var toolbar = ((AEBaseScreenAccessor) this).ae2lt$getVerticalToolbar();
        var buttons = ((VerticalButtonBarAccessor) toolbar).ae2lt$getButtons();
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i) instanceof SettingToggleButton<?> settingButton
                    && settingButton.getSetting() == Settings.VIEW_MODE) {
                var replacement = new TianshuViewModeButton();
                buttons.set(i, replacement);
                return replacement;
            }
        }
        throw new IllegalStateException("AE2 view-mode button is missing");
    }

    private void cycleViewMode(boolean reverse) {
        var current = menu.getConfigManager().getSetting(Settings.VIEW_MODE);
        ViewItems next;
        if (menu.maintainableView) {
            menu.setMaintainableView(false);
            next = reverse ? ViewItems.CRAFTABLE : ViewItems.ALL;
        } else if (reverse) {
            next = switch (current) {
                case ALL -> null;
                case STORED -> ViewItems.ALL;
                case CRAFTABLE -> ViewItems.STORED;
            };
        } else {
            next = switch (current) {
                case ALL -> ViewItems.STORED;
                case STORED -> ViewItems.CRAFTABLE;
                case CRAFTABLE -> null;
            };
        }
        if (next == null) {
            menu.setMaintainableView(true);
        } else {
            menu.getConfigManager().putSetting(Settings.VIEW_MODE, next);
        }
    }

    private final class TianshuViewModeButton extends IconButton {
        private TianshuViewModeButton() {
            super(ignored -> cycleViewMode(hasShiftDown()));
        }

        @Override
        protected Icon getIcon() {
            if (menu.maintainableView) return Icon.VIEW_MODE_CRAFTING;
            return switch (menu.getConfigManager().getSetting(Settings.VIEW_MODE)) {
                case ALL -> Icon.VIEW_MODE_ALL;
                case STORED -> Icon.VIEW_MODE_STORED;
                case CRAFTABLE -> Icon.VIEW_MODE_CRAFTING;
            };
        }

        @Override
        public java.util.List<Component> getTooltipMessage() {
            var value = menu.maintainableView
                    ? Component.translatable("ae2lt.tianshu.maintenance.view")
                    : switch (menu.getConfigManager().getSetting(Settings.VIEW_MODE)) {
                        case ALL -> Component.translatable(ButtonToolTips.StoredCraftable.getTranslationKey());
                        case STORED -> Component.translatable(ButtonToolTips.StoredItems.getTranslationKey());
                        case CRAFTABLE -> Component.translatable(ButtonToolTips.Craftable.getTranslationKey());
                    };
            return java.util.List.of(
                    Component.translatable(ButtonToolTips.View.getTranslationKey()), value);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && hasShiftDown()
                && getSlotUnderMouse() instanceof RepoSlot repoSlot) {
            if (!menu.maintenanceAvailable) {
                if (minecraft.player != null) minecraft.player.displayClientMessage(
                        Component.translatable("ae2lt.tianshu.maintenance.unavailable"), true);
                return true;
            }
            var entry = repoSlot.getEntry();
            if (entry != null && entry.getWhat() != null) {
                requestMaintenanceEditorFor(entry.getWhat());
                return true;
            }
        }

        if (minecraft.options.keyPickItem.matchesMouse(button)) {
            var slot = getSlotUnderMouse();
            if (menu.canModifyAmountForSlot(slot)) {
                var currentStack = GenericStack.fromItemStack(slot.getItem());
                if (currentStack != null) {
                    switchToScreen(new TianshuSetProcessingPatternAmountScreen<>(
                            this,
                            currentStack,
                            newStack -> {
                                ServerboundPacket message = new InventoryActionPacket(
                                        InventoryAction.SET_FILTER,
                                        slot.index,
                                        GenericStack.wrapInItemStack(newStack));
                                PacketDistributor.sendToServer(message);
                            }));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        if (menu.getCarried().isEmpty() && menu.canModifyAmountForSlot(hoveredSlot)) {
            var itemTooltip = new ArrayList<>(getTooltipFromContainerItem(hoveredSlot.getItem()));
            var unwrapped = GenericStack.fromItemStack(hoveredSlot.getItem());
            if (unwrapped != null) {
                itemTooltip.add(Tooltips.getAmountTooltip(ButtonToolTips.Amount, unwrapped));
            }
            itemTooltip.add(Tooltips.getSetAmountTooltip());
            drawTooltip(graphics, x, y, itemTooltip);
        } else {
            super.renderTooltip(graphics, x, y);
        }
    }

    @Override
    protected EmptyingAction getEmptyingAction(Slot slot, ItemStack carried) {
        if (menu.isProcessingPatternSlot(slot)) {
            var emptyingAction = ContainerItemStrategies.getEmptyingAction(carried);
            if (emptyingAction != null) {
                return emptyingAction;
            }
        }
        return super.getEmptyingAction(slot, carried);
    }

    /** Also used by the dedicated maintenance overview for zero-stock entries. */
    public void requestMaintenanceEditorFor(appeng.api.stacks.AEKey key) {
        if (key == null) return;
        requestedMaintenanceRevision = menu.getMaintenanceEditorRevision();
        awaitingMaintenanceEditor = true;
        menu.requestMaintenanceEditor(key);
    }

    @Override
    public void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);

        if (shouldShowCraftableIndicatorForSlot(slot)) {
            var poseStack = graphics.pose();
            poseStack.pushPose();
            poseStack.translate(0, 0, 100);
            StackSizeRenderer.renderSizeLabel(graphics, font, slot.x - 11, slot.y - 11, "+", false);
            poseStack.popPose();
        }

        if (!(slot instanceof RepoSlot repoSlot) || repoSlot.getEntry() == null) return;
        var summary = menu.getMaintenanceSummary().get(repoSlot.getEntry().getWhat());
        if (summary == null) return;
        int color = switch (InventoryMaintenanceBadge.from(summary.status())) {
            case GREEN -> 0xFF33CC44;
            case YELLOW -> 0xFFFFCC33;
            case RED -> 0xFFDD3333;
            case GRAY -> 0xFF888888;
        };
        graphics.fill(slot.x + 12, slot.y, slot.x + 16, slot.y + 4, color);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var lines = super.getTooltipFromContainerItem(stack);
        if (hoveredSlot != null && shouldShowCraftableIndicatorForSlot(hoveredSlot)) {
            lines = new ArrayList<>(lines);
            lines.add(ButtonToolTips.Craftable.text().withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private boolean shouldShowCraftableIndicatorForSlot(Slot slot) {
        var semantic = menu.getSlotSemantic(slot);
        if (semantic == SlotSemantics.CRAFTING_GRID
                || semantic == SlotSemantics.PROCESSING_INPUTS
                || semantic == SlotSemantics.SMITHING_TABLE_ADDITION
                || semantic == SlotSemantics.SMITHING_TABLE_BASE
                || semantic == SlotSemantics.SMITHING_TABLE_TEMPLATE
                || semantic == SlotSemantics.STONECUTTING_INPUT) {
            var slotContent = GenericStack.fromItemStack(slot.getItem());
            return slotContent != null && repo.isCraftable(slotContent.what());
        }
        return false;
    }

    @Override
    public void onClose() {
        if (config.isClearGridOnClose()) {
            menu.clear();
        }
        super.onClose();
    }

    private final class DerivedModePanel implements ICompositeWidget {
        private int x;
        private int y;
        private TianshuEncodingMode mode;

        @Override public boolean isVisible() { return mode != null; }
        @Override public void setPosition(Point position) { x = position.getX(); y = position.getY(); }
        @Override public void setSize(int width, int height) { }
        @Override public Rect2i getBounds() { return new Rect2i(x, y, 158, 66); }

        @Override
        public void drawForegroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
            if (mode == null) return;
            var label = switch (mode) {
                case ADVANCED -> Component.translatable("ae2lt.tianshu.terminal.mode.advanced");
                case OVERLOAD -> Component.translatable("ae2lt.tianshu.terminal.mode.overload");
                case CLOSED_LOOP -> Component.translatable("ae2lt.tianshu.terminal.mode.closed_loop");
                default -> Component.empty();
            };
            graphics.drawString(font, label, x + 8, y + 12, 0x404040, false);
            if (mode == TianshuEncodingMode.ADVANCED) drawAdvanced(graphics);
            else if (mode == TianshuEncodingMode.OVERLOAD) drawOverload(graphics);
            else drawClosedLoop(graphics);
        }

        private void drawAdvanced(GuiGraphics graphics) {
            for (int i = 0; i < 9; i++) {
                var stack = menu.getProcessingInputSlots()[i].getItem();
                if (stack.isEmpty()) continue;
                int sx = x + 8 + i * 17;
                int sy = y + 30;
                graphics.renderItem(stack, sx, sy);
                int direction = menu.getAdvancedDirection(i);
                String marker = direction == 0 ? "*" : shortDirection(Direction.values()[direction - 1]);
                graphics.drawString(font, marker, sx + 10, sy + 10, 0xFFFFFF, true);
            }
        }

        private void drawOverload(GuiGraphics graphics) {
            for (int i = 0; i < Math.min(9, menu.overloadState.inputSlots().size()); i++) {
                var stack = menu.getProcessingInputSlots()[i].getItem();
                int sx = x + 8 + i * 17;
                int sy = y + 27;
                graphics.renderItem(stack, sx, sy);
                int color = menu.overloadState.inputMode(i) == MatchMode.ID_ONLY ? 0xFFFF55 : 0x55FF55;
                graphics.fill(sx + 12, sy + 12, sx + 16, sy + 16, color);
            }
            for (int i = 0; i < Math.min(9, menu.overloadState.outputSlots().size()); i++) {
                var stack = menu.getProcessingOutputSlots()[i].getItem();
                int sx = x + 8 + i * 17;
                int sy = y + 45;
                graphics.renderItem(stack, sx, sy);
                int color = menu.overloadState.outputMode(i) == MatchMode.ID_ONLY ? 0xFFFF55 : 0x55FF55;
                graphics.fill(sx + 12, sy + 12, sx + 16, sy + 16, color);
            }
        }

        private void drawClosedLoop(GuiGraphics graphics) {
            graphics.drawString(font, Component.translatable(
                    "ae2lt.tianshu.terminal.closed_loop.candidate",
                    menu.closedLoopCandidateCount == 0 ? 0 : menu.closedLoopCandidateIndex + 1,
                    menu.closedLoopCandidateCount), x + 8, y + 29, 0x666666, false);
            graphics.drawString(font, Component.translatable(
                    "ae2lt.tianshu.terminal.closed_loop.seed_multipliers",
                    menu.closedLoopExecutionSeedMultiplier,
                    menu.closedLoopStoredTaskMultiplier),
                    x + 8, y + 43, 0x666666, false);
            if (menu.closedLoopEncodeState != 0) {
                graphics.drawString(font, Component.translatable(
                        menu.closedLoopEncodeState == 1
                                ? "ae2lt.tianshu.terminal.closed_loop.invalid_pattern"
                                : "ae2lt.tianshu.terminal.closed_loop.invalid_configuration"),
                        x + 8, y + 55, 0xAA2222, false);
            } else if (menu.uploadState != 0) {
                graphics.drawString(font, Component.translatable(
                        menu.uploadState == 1 ? "ae2lt.tianshu.terminal.upload.success"
                                : "ae2lt.tianshu.terminal.upload.failed"),
                        x + 8, y + 55, menu.uploadState == 1 ? 0x228822 : 0xAA2222, false);
            }
        }

        @Override
        public boolean onMouseDown(Point mouse, int button) {
            if (button != 0 || mode == null) return false;
            if (mode == TianshuEncodingMode.ADVANCED) {
                int slot = iconAt(mouse, y + 30);
                if (slot >= 0 && !menu.getProcessingInputSlots()[slot].getItem().isEmpty()) {
                    menu.cycleAdvancedDirection(slot);
                    return true;
                }
            } else if (mode == TianshuEncodingMode.OVERLOAD) {
                int input = iconAt(mouse, y + 27);
                if (input >= 0 && input < menu.overloadState.inputSlots().size()) {
                    menu.toggleOverloadInput(input);
                    return true;
                }
                int output = iconAt(mouse, y + 45);
                if (output >= 0 && output < menu.overloadState.outputSlots().size()) {
                    menu.toggleOverloadOutput(output);
                    return true;
                }
            }
            return false;
        }

        private int iconAt(Point mouse, int rowY) {
            if (mouse.getY() < rowY || mouse.getY() >= rowY + 16
                    || mouse.getX() < x + 8 || mouse.getX() >= x + 161) return -1;
            int slot = (mouse.getX() - x - 8) / 17;
            int within = (mouse.getX() - x - 8) % 17;
            return within < 16 && slot < 9 ? slot : -1;
        }

        private String shortDirection(Direction direction) {
            return switch (direction) {
                case DOWN -> "D";
                case UP -> "U";
                case NORTH -> "N";
                case SOUTH -> "S";
                case WEST -> "W";
                case EAST -> "E";
            };
        }
    }

}
