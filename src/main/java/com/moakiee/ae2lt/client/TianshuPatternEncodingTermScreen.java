package com.moakiee.ae2lt.client;

import appeng.client.gui.Icon;
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
import appeng.util.ReadableNumberConverter;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternEncodingType;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternUploadRouting;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.mixin.client.AEBaseScreenAccessor;
import com.moakiee.ae2lt.mixin.client.VerticalButtonBarAccessor;
import com.moakiee.ae2lt.registry.ModItems;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
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
    private final TianshuClosedLoopEncodingPanel closedLoopPanel;
    private final List<AE2Button> processingModeButtons;
    private final AE2Button advancedEncoding;
    private final AE2Button overloadEncoding;
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

        addExtraTab(TianshuEncodingMode.CLOSED_LOOP, ModItems.CLOSED_LOOP_PATTERN.get().getDefaultInstance(),
                Component.translatable("ae2lt.tianshu.terminal.mode.closed_loop"), "modeTabButton4");
        closedLoopPanel = new TianshuClosedLoopEncodingPanel(this, widgets,
                () -> switchToScreen(new TianshuClosedLoopPatternConfigScreen<>(this)));
        widgets.add("closedLoopPanel", closedLoopPanel);

        processingModeButtons = List.of(
                addCompactButton("processingMultiply2", Component.literal("×2"),
                        () -> menu.multiplyProcessing(hasShiftDown() ? 4 : 2)),
                addCompactButton("processingMultiply5", Component.literal("×5"),
                        () -> menu.multiplyProcessing(hasShiftDown() ? 10 : 5)),
                addCompactButton("processingDivide2", Component.literal("÷2"),
                        () -> menu.multiplyProcessing(hasShiftDown() ? -4 : -2)),
                addCompactButton("processingDivide5", Component.literal("÷5"),
                        () -> menu.multiplyProcessing(hasShiftDown() ? -10 : -5)));
        advancedEncoding = addCompactButton("advancedEncodingButton",
                Component.translatable("ae2lt.tianshu.terminal.encoding.advanced.short"),
                () -> switchToScreen(new TianshuAdvancedPatternConfigScreen<>(this)));
        overloadEncoding = addCompactButton("overloadEncodingButton",
                Component.translatable("ae2lt.tianshu.terminal.encoding.overload.short"),
                () -> switchToScreen(new TianshuOverloadPatternConfigScreen<>(this)));
        upload = widgets.addButton("tianshuUpload", Component.translatable("ae2lt.tianshu.terminal.upload"),
                this::openUploadScreen);
        tianshuTarget = widgets.addButton("tianshuTarget", Component.empty(),
                () -> menu.cycleTianshuTarget(hasShiftDown() ? -1 : 1));
        replaceViewModeButton();
        globalReserve = widgets.addButton("globalReserve",
                Component.translatable("ae2lt.tianshu.reserve.button"),
                () -> switchToScreen(new TianshuGlobalReserveScreen<>(this)));
    }

    private AE2Button addCompactButton(String widgetId, Component label, Runnable onPress) {
        var button = new CompactAE2Button(label, ignored -> onPress.run());
        widgets.add(widgetId, button);
        return button;
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
        boolean processing = selected == TianshuEncodingMode.PROCESSING;
        processingModeButtons.forEach(button -> button.visible = processing);
        boolean hasDraftInput = hasProcessingDraftInput();
        updateEncodingButton(advancedEncoding, ProcessingPatternEncodingType.ADVANCED,
                processing && AdvancedAECompat.isLoaded(), hasDraftInput, "advanced");
        updateEncodingButton(overloadEncoding, ProcessingPatternEncodingType.OVERLOAD,
                processing, hasDraftInput, "overload");
        boolean closedLoop = selected == TianshuEncodingMode.CLOSED_LOOP;
        closedLoopPanel.setVisible(closedLoop);
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_EXTERNAL_INPUT, true);
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_SEED_INPUT, true);
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

    private void updateEncodingButton(AE2Button button, ProcessingPatternEncodingType type,
                                      boolean visible, boolean enabled, String key) {
        button.visible = visible;
        button.active = enabled;
        boolean armed = menu.processingEncodingType == type;
        button.setMessage(Component.translatable(
                "ae2lt.tianshu.terminal.encoding." + key + ".short")
                .withStyle(armed ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        button.setTooltip(Tooltip.create(Component.translatable(
                "ae2lt.tianshu.terminal.encoding." + key + (armed ? ".armed" : ""))));
    }

    private boolean hasProcessingDraftInput() {
        for (var slot : menu.getProcessingInputSlots()) {
            if (!slot.getItem().isEmpty()) return true;
        }
        return false;
    }

    ItemStack firstEncodedPattern() {
        return menu.getSlots(SlotSemantics.ENCODED_PATTERN).stream()
                .map(Slot::getItem).filter(item -> !item.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
    }

    private void openUploadScreen() {
        var stack = firstEncodedPattern();
        if (stack.isEmpty()) return;
        // The server is authoritative for validating a closed-loop payload. Routing by the
        // item type here keeps the shared upload button responsive even when the client cannot
        // decode a registry-backed payload and lets the server report a proper upload failure.
        if (stack.getItem() instanceof ClosedLoopPatternItem) {
            menu.uploadEncodedPattern();
            return;
        }
        var route = minecraft.level != null
                ? TianshuPatternUploadRouting.classify(stack, minecraft.level)
                : TianshuPatternUploadRouting.Route.INVALID;
        switch (route) {
            case CLOSED_LOOP_STORAGE, CRAFTING_ASSEMBLER -> menu.uploadEncodedPattern();
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
            if (isClosedLoopMemberSlot(slot) && slot.hasItem()) {
                int memberIndex = slot.getContainerSlot();
                var key = AEItemKey.of(slot.getItem());
                long copies = Math.max(1L, menu.closedLoopDraftSync.copies(memberIndex));
                switchToScreen(new TianshuSetProcessingPatternAmountScreen<>(
                        this,
                        new GenericStack(key, copies),
                        newStack -> {
                            if (newStack == null) {
                                ServerboundPacket message = new InventoryActionPacket(
                                        InventoryAction.SET_FILTER, slot.index, ItemStack.EMPTY);
                                PacketDistributor.sendToServer(message);
                            } else {
                                menu.setClosedLoopMemberCopies(memberIndex, newStack.amount());
                            }
                        }));
                return true;
            }
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
        if (menu.getCarried().isEmpty()
                && closedLoopPanel.isMouseOverStatus(x - leftPos, y - topPos)) {
            drawTooltip(graphics, x, y, closedLoopPanel.buildStatusTooltip());
            return;
        }
        if (menu.getCarried().isEmpty() && menu.canModifyAmountForSlot(hoveredSlot)) {
            var itemTooltip = new ArrayList<>(getTooltipFromContainerItem(hoveredSlot.getItem()));
            var unwrapped = GenericStack.fromItemStack(hoveredSlot.getItem());
            if (unwrapped != null) {
                itemTooltip.add(Tooltips.getAmountTooltip(ButtonToolTips.Amount, unwrapped));
            }
            itemTooltip.add(Tooltips.getSetAmountTooltip());
            drawTooltip(graphics, x, y, itemTooltip);
        } else if (menu.getCarried().isEmpty() && isClosedLoopMemberSlot(hoveredSlot)
                && hoveredSlot.hasItem()) {
            var itemTooltip = new ArrayList<>(getTooltipFromContainerItem(hoveredSlot.getItem()));
            TianshuClosedLoopEncodingPanel.appendMemberTooltip(itemTooltip, menu,
                    hoveredSlot.getContainerSlot(), hoveredSlot.getItem(), minecraft.level);
            itemTooltip.add(Tooltips.getSetAmountTooltip());
            drawTooltip(graphics, x, y, itemTooltip);
        } else {
            super.renderTooltip(graphics, x, y);
        }
    }

    private boolean isClosedLoopMemberSlot(Slot slot) {
        return slot != null
                && menu.getSlotSemantic(slot) == Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_MEMBER;
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

        if (isClosedLoopMemberSlot(slot) && slot.hasItem()) {
            long copies = Math.max(1L, menu.closedLoopDraftSync.copies(slot.getContainerSlot()));
            StackSizeRenderer.renderSizeLabel(graphics, font, slot.x, slot.y,
                    ReadableNumberConverter.format(copies, 4));
        }

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

    /** AE2 button visuals with a compact text layer for the processing-mode controls. */
    private static final class CompactAE2Button extends AE2Button {
        private static final float TEXT_SCALE = 0.65F;

        private CompactAE2Button(Component message, Button.OnPress onPress) {
            super(message, onPress);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            var message = getMessage();
            setMessage(Component.empty());
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            setMessage(message);

            var font = Minecraft.getInstance().font;
            int color;
            int yOffset;
            if (!active) {
                color = 0xFF413F54;
                yOffset = -1;
            } else if (isHovered()) {
                color = 0xFF517497;
                yOffset = 0;
            } else {
                color = 0xFFF2F2F2;
                yOffset = 1;
            }

            float virtualWidth = getWidth() / TEXT_SCALE;
            float virtualHeight = getHeight() / TEXT_SCALE;
            float textX = (virtualWidth - font.width(message)) / 2.0F;
            float textY = (virtualHeight - 9.0F) / 2.0F + 1.0F - yOffset / TEXT_SCALE;
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(getX(), getY(), 10.0F);
            pose.scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
            graphics.drawString(font, message, Math.round(textX), Math.round(textY), color, false);
            pose.popPose();
        }
    }

}
