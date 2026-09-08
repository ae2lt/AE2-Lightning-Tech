package com.moakiee.ae2lt.client;

import appeng.client.gui.Icon;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
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
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.GenericStack;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.core.definitions.AEItems;
import appeng.core.network.ServerboundPacket;
import appeng.core.network.bidirectional.ConfigValuePacket;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.parts.encoding.EncodingMode;
import appeng.api.stacks.AEItemKey;
import appeng.util.prioritylist.IPartitionList;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import appeng.client.gui.me.common.RepoSlot;
import org.lwjgl.glfw.GLFW;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceBadge;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import com.moakiee.ae2lt.logic.AdvancedAECompat;
import org.anti_ad.mc.ipn.api.IPNIgnore;
import org.jetbrains.annotations.Nullable;

@IPNIgnore
public class TianshuPatternEncodingTermScreen<M extends TianshuPatternEncodingTermMenu>
        extends TianshuMaintenanceTermScreen<M> {
    private final Map<EncodingMode, TianshuEncodingModePanel> modePanels =
            new EnumMap<>(EncodingMode.class);
    private final Map<TianshuEncodingMode, TabButton> modeTabs =
            new EnumMap<>(TianshuEncodingMode.class);
    private final TianshuClosedLoopEncodingPanel closedLoopPanel;
    private final TianshuOmniversalEncodingPanel omniversalPanel;
    private final List<ProcessingMultiplierButton> processingModeButtons;
    private final AE2Button advancedEncoding;
    private final AE2Button overloadEncoding;
    private final RepoSlot networkBlankPatternSlot;
    private final Item blankPatternItem;
    @Nullable
    private GridInventoryEntry cachedNetworkBlankPatternEntry;
    private int observedEncodingAck;

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

        widgets.add("encodePattern", new ActionButton(ActionItems.ENCODE, action -> menu.encode()) {
            @Override
            public List<Component> getTooltipMessage() {
                return menu.tianshuMode == TianshuEncodingMode.OMNIVERSAL
                        && TianshuUploadTriggerClient.shouldTrigger()
                        ? List.of(Component.translatable("ae2lt.tianshu.omniversal.encode_upload"),
                                Component.translatable("ae2lt.tianshu.omniversal.encode_upload.tooltip"))
                        : super.getTooltipMessage();
            }
        });

        addExtraTab(TianshuEncodingMode.CLOSED_LOOP, ModItems.CLOSED_LOOP_PATTERN.get().getDefaultInstance(),
                Component.translatable("ae2lt.tianshu.terminal.mode.closed_loop"), "modeTabButton4");
        closedLoopPanel = new TianshuClosedLoopEncodingPanel(this, widgets,
                () -> switchToScreen(new TianshuClosedLoopPatternConfigScreen<>(this)));
        widgets.add("closedLoopPanel", closedLoopPanel);
        if (com.moakiee.ae2lt.integration.useless.UselessModCompat.isLoaded()) {
            addExtraTab(TianshuEncodingMode.OMNIVERSAL,
                    com.moakiee.ae2lt.integration.useless.UselessModCompat.icon(),
                    Component.translatable("ae2lt.tianshu.terminal.mode.omniversal"), "modeTabButton5");
        }
        omniversalPanel = new TianshuOmniversalEncodingPanel(this, widgets);
        widgets.add("omniversalPanel", omniversalPanel);

        processingModeButtons = List.of(
                addProcessingMultiplierButton("processingMultiply2", 2, 4),
                addProcessingMultiplierButton("processingMultiply5", 5, 10),
                addProcessingMultiplierButton("processingDivide2", -2, -4),
                addProcessingMultiplierButton("processingDivide5", -5, -10));
        advancedEncoding = addCompactButton("advancedEncodingButton",
                Component.translatable("ae2lt.tianshu.terminal.encoding.advanced.short"),
                () -> switchToScreen(new TianshuAdvancedPatternConfigScreen<>(this)));
        overloadEncoding = addCompactButton("overloadEncodingButton",
                Component.translatable("ae2lt.tianshu.terminal.encoding.overload.short"),
                () -> switchToScreen(new TianshuOverloadPatternConfigScreen<>(this)));
        blankPatternItem = AEItems.BLANK_PATTERN.asItem();
        networkBlankPatternSlot = new NetworkBlankPatternSlot(repo);
        observedEncodingAck = menu.triggeredUploadAck;
    }

    @Override
    public void init() {
        super.init();
        var blankPatternSlots = menu.getSlots(SlotSemantics.BLANK_PATTERN);
        if (!blankPatternSlots.isEmpty()) {
            var disabledSlot = blankPatternSlots.getFirst();
            networkBlankPatternSlot.x = disabledSlot.x;
            networkBlankPatternSlot.y = disabledSlot.y;
            menu.slots.add(networkBlankPatternSlot);
        }
    }

    private AE2Button addCompactButton(String widgetId, Component label, Runnable onPress) {
        var button = new CompactAE2Button(label, ignored -> onPress.run());
        widgets.add(widgetId, button);
        return button;
    }

    private ProcessingMultiplierButton addProcessingMultiplierButton(
            String widgetId, int factor, int shiftedFactor) {
        var button = addCompactButton(widgetId, processingMultiplierLabel(factor),
                () -> menu.multiplyProcessing(hasShiftDown() ? shiftedFactor : factor));
        return new ProcessingMultiplierButton(button, factor, shiftedFactor);
    }

    private static Component processingMultiplierLabel(int factor) {
        return Component.literal((factor < 0 ? "÷" : "×") + Math.abs(factor));
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
        observeEncodingAck();
        var selected = menu.tianshuMode;
        for (var mode : EncodingMode.values()) {
            var modeSelected = selected.ae2Mode() == mode;
            modePanels.get(mode).setVisible(modeSelected);
        }
        modeTabs.forEach((mode, button) -> button.setSelected(mode == selected));
        if (menu.hasTriggeredUploadAck()
                && TianshuRecipeTransferContext.isEncodingResultReady(
                        menu, firstEncodedPattern())
                && menu.consumeTriggeredUpload()) {
            openUploadScreen(menu.consumeDirectUploadRequest());
            return;
        }
        boolean processing = selected == TianshuEncodingMode.PROCESSING;
        boolean shifted = hasShiftDown();
        processingModeButtons.forEach(control -> {
            control.button().visible = processing;
            control.button().setMessage(processingMultiplierLabel(
                    shifted ? control.shiftedFactor() : control.factor()));
        });
        boolean hasDraftInput = hasProcessingDraftInput();
        updateEncodingButton(advancedEncoding, ProcessingPatternEncodingType.ADVANCED,
                processing && AdvancedAECompat.isLoaded(), hasDraftInput, "advanced");
        updateEncodingButton(overloadEncoding, ProcessingPatternEncodingType.OVERLOAD,
                processing, hasDraftInput, "overload");
        boolean closedLoop = selected == TianshuEncodingMode.CLOSED_LOOP;
        closedLoopPanel.setVisible(closedLoop);
        omniversalPanel.setVisible(selected == TianshuEncodingMode.OMNIVERSAL);
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_GLOBAL_RESERVE_MARK, true);
    }

    private void observeEncodingAck() {
        var current = firstEncodedPattern();
        if (observedEncodingAck != menu.triggeredUploadAck) {
            observedEncodingAck = menu.triggeredUploadAck;
            TianshuRecipeTransferContext.acceptEncodedPattern(menu, current);
        }
    }

    private void updateEncodingButton(AE2Button button, ProcessingPatternEncodingType type,
                                      boolean visible, boolean enabled, String key) {
        button.visible = visible;
        button.active = enabled;
        boolean armed = menu.processingEncodingType.includes(type);
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

    private void openUploadScreen(boolean directUploadRequested) {
        var stack = firstEncodedPattern();
        if (stack.isEmpty()) return;
        // The server is authoritative for validating a closed-loop payload. Routing by the
        // item type here keeps the shared upload button responsive even when the client cannot
        // decode a registry-backed payload and lets the server report a proper upload failure.
        if (stack.getItem() instanceof ClosedLoopPatternItem
                || com.moakiee.ae2lt.integration.useless.UselessModCompat.isOmniversalPattern(stack)) {
            menu.uploadEncodedPattern();
            return;
        }
        var route = minecraft.level != null
                ? TianshuPatternUploadRouting.classify(stack, minecraft.level)
                : TianshuPatternUploadRouting.Route.INVALID;
        switch (route) {
            case CLOSED_LOOP_STORAGE, CRAFTING_ASSEMBLER, OMNIVERSAL_FURNACE -> menu.uploadEncodedPattern();
            case PROCESSING_PROVIDER -> switchToScreen(
                    new TianshuUploadTargetScreen<>(this, directUploadRequested));
            case INVALID -> { }
        }
    }

    /** Opens the provider picker immediately after a recipe viewer restores this parent screen. */
    public boolean openDirectUploadFallback() {
        if (!menu.consumeTriggeredUpload()) return false;
        if (!menu.consumeDirectUploadRequest()) return false;
        switchToScreen(new TianshuUploadTargetScreen<>(this, true));
        return true;
    }











    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleMaintenanceClick(button)) return true;

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
        var omniversalTooltip = omniversalPanel.tooltipAt(x - leftPos, y - topPos);
        if (menu.getCarried().isEmpty() && omniversalTooltip != null) {
            drawTooltip(graphics, x, y, omniversalTooltip);
            return;
        }
        var multiplierTooltip = closedLoopPanel.getMultiplierTooltipAt(
                x - leftPos, y - topPos);
        if (menu.getCarried().isEmpty() && multiplierTooltip != null) {
            drawTooltip(graphics, x, y, multiplierTooltip);
            return;
        }
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




    @Override
    public void renderSlot(GuiGraphics graphics, Slot slot) {
        if (slot == networkBlankPatternSlot && !slot.hasItem()) {
            Icon.BACKGROUND_BLANK_PATTERN.getBlitter()
                    .dest(slot.x, slot.y)
                    .blit(graphics);
        }

        if (isClosedLoopMemberSlot(slot) && slot.hasItem()) {
            // Render only the pattern icon here. The standard slot decoration would add the
            // display stack's amount (1) in the same corner as the per-cycle copy count below.
            graphics.renderItem(slot.getItem().copyWithCount(1), slot.x, slot.y);
            long copies = Math.max(1L,
                    menu.closedLoopDraftSync.copies(slot.getContainerSlot()));
            if (copies > 1L) {
                var poseStack = graphics.pose();
                poseStack.pushPose();
                // Items render at z=100; keep the authoritative per-cycle count above the icon.
                poseStack.translate(0, 0, 100);
                StackSizeRenderer.renderSizeLabel(
                        graphics, font, slot.x, slot.y, Long.toString(copies), false);
                poseStack.popPose();
            }
        } else {
            super.renderSlot(graphics, slot);
        }

        if (shouldShowCraftableIndicatorForSlot(slot)) {
            var poseStack = graphics.pose();
            poseStack.pushPose();
            poseStack.translate(0, 0, 100);
            StackSizeRenderer.renderSizeLabel(graphics, font, slot.x - 11, slot.y - 11, "+", false);
            poseStack.popPose();
        }


    }

    /**
     * AE2 only keeps meaningful network entries in its client repository. A configured rule can
     * intentionally have zero stock and no longer have a pattern, so the maintainable view adds a
     * bounded synthetic entry for that otherwise-invisible key. The entry remains client-only and
     * every interaction except opening the maintenance editor is swallowed above.
     */


    /** Filters the visible view without deleting entries from AE2's client repository. */












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

    private GridInventoryEntry findNetworkBlankPatternEntry() {
        if (cachedNetworkBlankPatternEntry != null
                && repo.getAllEntries().contains(cachedNetworkBlankPatternEntry)
                && isBlankPatternEntry(cachedNetworkBlankPatternEntry)) {
            return cachedNetworkBlankPatternEntry;
        }

        cachedNetworkBlankPatternEntry = null;
        for (var entry : repo.getAllEntries()) {
            if (isBlankPatternEntry(entry)) {
                cachedNetworkBlankPatternEntry = entry;
                return entry;
            }
        }
        return null;
    }

    private boolean isBlankPatternEntry(GridInventoryEntry entry) {
        return entry.getWhat() instanceof AEItemKey key && key.getItem() == blankPatternItem;
    }

    private final class NetworkBlankPatternSlot extends RepoSlot {
        private NetworkBlankPatternSlot(Repo repo) {
            super(repo, 0, 0, 0);
        }

        @Override
        public GridInventoryEntry getEntry() {
            return repo.isEnabled() ? findNetworkBlankPatternEntry() : null;
        }
    }

    @Override
    public void onClose() {
        if (config.isClearGridOnClose()) {
            menu.clear();
        }
        super.onClose();
    }

    /** AE2 button visuals with a compact text layer for the processing-mode controls. */
    private record ProcessingMultiplierButton(AE2Button button, int factor, int shiftedFactor) {
    }

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
