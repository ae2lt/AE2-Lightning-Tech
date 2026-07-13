package com.moakiee.ae2lt.client;

import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.TabButton;
import appeng.client.gui.widgets.TabButton.Style;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.mixin.client.PatternEncodingTermScreenAccessor;
import com.moakiee.ae2lt.registry.ModItems;
import java.util.EnumMap;
import java.util.Map;
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
import com.moakiee.ae2lt.logic.AdvancedAECompat;

public class TianshuPatternEncodingTermScreen<M extends TianshuPatternEncodingTermMenu>
        extends PatternEncodingTermScreen<M> {
    private final Map<TianshuEncodingMode, TabButton> extraTabs =
            new EnumMap<>(TianshuEncodingMode.class);
    private final DerivedModePanel derivedPanel = new DerivedModePanel();
    private final AE2Button multiply2;
    private final AE2Button multiply5;
    private final AE2Button divide2;
    private final AE2Button divide5;
    private final AE2Button previousCandidate;
    private final AE2Button nextCandidate;
    private final AE2Button parallelDown;
    private final AE2Button parallelUp;
    private final AE2Button upload;
    private final AE2Button previousUploadTarget;
    private final AE2Button nextUploadTarget;
    private final UploadTargetPanel uploadTargetPanel = new UploadTargetPanel();
    private final AE2Button maintainableView;
    private final AE2Button globalReserve;
    private boolean awaitingMaintenanceEditor;
    private int requestedMaintenanceRevision;

    public TianshuPatternEncodingTermScreen(
            M menu,
            Inventory inventory,
            Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        addExtraTab(TianshuEncodingMode.ADVANCED, ModItems.OVERLOAD_PATTERN.get().getDefaultInstance(),
                Component.translatable("ae2lt.tianshu.terminal.mode.advanced"), "modeTabButton4");
        addExtraTab(TianshuEncodingMode.OVERLOAD, ModItems.OVERLOAD_PATTERN.get().getDefaultInstance(),
                Component.translatable("ae2lt.tianshu.terminal.mode.overload"), "modeTabButton5");
        addExtraTab(TianshuEncodingMode.CLOSED_LOOP, ModItems.CLOSED_LOOP_PATTERN.get().getDefaultInstance(),
                Component.translatable("ae2lt.tianshu.terminal.mode.closed_loop"), "modeTabButton6");
        widgets.add("derivedModePanel", derivedPanel);

        multiply2 = widgets.addButton("processingMultiply2", Component.literal("×2"),
                () -> menu.multiplyProcessing(hasShiftDown() ? 4 : 2));
        multiply5 = widgets.addButton("processingMultiply5", Component.literal("×5"),
                () -> menu.multiplyProcessing(hasShiftDown() ? 10 : 5));
        divide2 = widgets.addButton("processingDivide2", Component.literal("÷2"),
                () -> menu.multiplyProcessing(hasShiftDown() ? -4 : -2));
        divide5 = widgets.addButton("processingDivide5", Component.literal("÷5"),
                () -> menu.multiplyProcessing(hasShiftDown() ? -10 : -5));
        previousCandidate = widgets.addButton("closedLoopPrevious", Component.literal("<"),
                () -> menu.selectClosedLoopCandidate(-1));
        nextCandidate = widgets.addButton("closedLoopNext", Component.literal(">"),
                () -> menu.selectClosedLoopCandidate(1));
        parallelDown = widgets.addButton("closedLoopParallelDown", Component.literal("−"),
                () -> menu.changeClosedLoopParallelism(hasShiftDown() ? -10 : -1));
        parallelUp = widgets.addButton("closedLoopParallelUp", Component.literal("+"),
                () -> menu.changeClosedLoopParallelism(hasShiftDown() ? 10 : 1));
        upload = widgets.addButton("tianshuUpload", Component.translatable("ae2lt.tianshu.terminal.upload"),
                menu::uploadTianshuPattern);
        previousUploadTarget = widgets.addButton("uploadTargetPrevious", Component.literal("<"),
                () -> menu.cycleUploadTarget(-1));
        nextUploadTarget = widgets.addButton("uploadTargetNext", Component.literal(">"),
                () -> menu.cycleUploadTarget(1));
        widgets.add("uploadTargetPanel", uploadTargetPanel);
        maintainableView = widgets.addButton("maintainableView",
                Component.translatable("ae2lt.tianshu.maintenance.view"),
                () -> menu.setMaintainableView(!menu.maintainableView));
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
        extraTabs.put(mode, tab);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (awaitingMaintenanceEditor
                && menu.getMaintenanceEditorRevision() != requestedMaintenanceRevision
                && menu.getMaintenanceEditorData() != null) {
            awaitingMaintenanceEditor = false;
            switchToScreen(new TianshuMaintenanceRuleScreen<>(this, menu.getMaintenanceEditorData()));
            return;
        }
        var selected = menu.tianshuMode;
        boolean derived = !selected.isAe2Mode();
        if (derived) {
            var accessor = (PatternEncodingTermScreenAccessor) this;
            accessor.ae2lt$getModePanels().values().forEach(panel -> panel.setVisible(false));
            accessor.ae2lt$getModeTabButtons().values().forEach(button -> button.setSelected(false));
            setSlotsHidden(SlotSemantics.CRAFTING_GRID, true);
            setSlotsHidden(SlotSemantics.CRAFTING_RESULT, true);
            setSlotsHidden(SlotSemantics.PROCESSING_INPUTS, true);
            setSlotsHidden(SlotSemantics.PROCESSING_OUTPUTS, true);
            setSlotsHidden(SlotSemantics.SMITHING_TABLE_TEMPLATE, true);
            setSlotsHidden(SlotSemantics.SMITHING_TABLE_BASE, true);
            setSlotsHidden(SlotSemantics.SMITHING_TABLE_ADDITION, true);
            setSlotsHidden(SlotSemantics.SMITHING_TABLE_RESULT, true);
            setSlotsHidden(SlotSemantics.STONECUTTING_INPUT, true);
        }
        derivedPanel.mode = derived ? selected : null;
        extraTabs.forEach((mode, button) -> button.setSelected(mode == selected));
        var advancedTab = extraTabs.get(TianshuEncodingMode.ADVANCED);
        if (advancedTab != null) advancedTab.visible = AdvancedAECompat.isLoaded();
        boolean processing = selected == TianshuEncodingMode.PROCESSING;
        multiply2.visible = processing;
        multiply5.visible = processing;
        divide2.visible = processing;
        divide5.visible = processing;
        boolean closedLoop = selected == TianshuEncodingMode.CLOSED_LOOP;
        previousCandidate.visible = closedLoop;
        nextCandidate.visible = closedLoop;
        parallelDown.visible = closedLoop;
        parallelUp.visible = closedLoop;
        upload.visible = true;
        upload.active = closedLoop ? menu.encodedClosedLoop : menu.uploadTargetFreeSlots > 0;
        boolean providerUpload = !closedLoop;
        previousUploadTarget.visible = providerUpload;
        nextUploadTarget.visible = providerUpload;
        uploadTargetPanel.visible = providerUpload;
        maintainableView.active = menu.maintenanceAvailable;
        globalReserve.active = menu.maintenanceAvailable;
        maintainableView.setMessage(Component.translatable(menu.maintainableView
                ? "ae2lt.tianshu.maintenance.view.on" : "ae2lt.tianshu.maintenance.view"));
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
                requestedMaintenanceRevision = menu.getMaintenanceEditorRevision();
                awaitingMaintenanceEditor = true;
                menu.requestMaintenanceEditor(entry.getWhat());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
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
                    "ae2lt.tianshu.terminal.closed_loop.parallel", menu.closedLoopParallelism),
                    x + 8, y + 43, 0x666666, false);
            if (menu.uploadState != 0) {
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

    private final class UploadTargetPanel implements ICompositeWidget {
        private int x;
        private int y;
        private boolean visible;

        @Override public boolean isVisible() { return visible; }
        @Override public void setPosition(Point position) { x = position.getX(); y = position.getY(); }
        @Override public void setSize(int width, int height) { }
        @Override public Rect2i getBounds() { return new Rect2i(x, y, 96, 26); }

        @Override
        public void drawForegroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
            if (!visible) return;
            String name = menu.uploadTargetName == null || menu.uploadTargetName.isBlank()
                    ? Component.translatable("ae2lt.tianshu.terminal.upload.no_target").getString()
                    : menu.uploadTargetName;
            graphics.drawString(font, font.plainSubstrByWidth(name, 94), x, y, 0x404040, false);
            graphics.drawString(font, Component.translatable(
                    "ae2lt.tianshu.terminal.upload.free", menu.uploadTargetFreeSlots),
                    x, y + 11, menu.uploadTargetFreeSlots > 0 ? 0x228822 : 0xAA2222, false);
        }
    }
}
