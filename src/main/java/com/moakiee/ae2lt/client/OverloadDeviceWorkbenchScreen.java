package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.menu.OverloadDeviceWorkbenchMenu;

public class OverloadDeviceWorkbenchScreen extends AbstractContainerScreen<OverloadDeviceWorkbenchMenu> {

    // ── Colors (hub style, Appendix C) ──
    private static final int BG_DEEP = 0xFF1E1E1E;
    private static final int BG_LIGHT = 0xFF313131;
    private static final int BG_PANEL = 0xFF262626;
    private static final int HIGHLIGHT_GOLD = 0xFFF6D365;
    private static final int ENERGY_GREEN = 0xFF36B65C;
    private static final int SLOT_BORDER_LIGHT = 0xFF8B8B8B;
    private static final int SLOT_BORDER_DARK = 0xFF080808;
    private static final int SLOT_BG = 0xFF1B1B1B;
    private static final int TEXT_PRIMARY = 0xE0E0E0;
    private static final int TEXT_SECONDARY = 0x8B8B8B;
    private static final int REMOVE_RED = 0xFF7A2A2A;
    private static final int REMOVE_RED_HOVER = 0xFFC24848;
    private static final int GRID_ONLINE = 0xFF36B65C;
    private static final int GRID_OFFLINE = 0xFFFF6060;
    private static final int PROGRESS_BG = 0xFF1A1A1A;

    // ── Layout ──
    private static final int ROW_HEIGHT = 20;
    private static final int REMOVE_BUTTON_SIZE = 12;
    private static final int REMOVE_BUTTON_MARGIN = 4;
    private static final int VISIBLE_ROWS = 4;
    private static final int BAR_HEIGHT = 6;

    // Status area
    private static final int STATUS_X = 34;
    private static final int STATUS_Y = 18;

    // Module list area
    private static final int MODULE_LIST_X = 34;
    private static final int MODULE_LIST_Y = 72;
    private static final int MODULE_LIST_WIDTH = 156;

    // Progress bar
    private static final int PROGRESS_BAR_WIDTH = 80;
    private static final int PROGRESS_BAR_HEIGHT = 6;

    private int scrollOffset = 0;

    public OverloadDeviceWorkbenchScreen(OverloadDeviceWorkbenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 198;
        this.imageHeight = 244;
        this.inventoryLabelX = OverloadDeviceWorkbenchMenu.INVENTORY_X;
        this.inventoryLabelY = OverloadDeviceWorkbenchMenu.INVENTORY_Y - 10;
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        gfx.fill(x, y, x + imageWidth, y + imageHeight, BG_DEEP);
        gfx.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, BG_LIGHT);

        // Left column panel (device + structural slots)
        gfx.fill(x + 4, y + 14, x + 30, y + OverloadDeviceWorkbenchMenu.INPUT_Y + 20, BG_DEEP);

        // Status area panel
        gfx.fill(x + STATUS_X - 2, y + STATUS_Y - 2, x + imageWidth - 4, y + STATUS_Y + 34, BG_PANEL);

        // Module list panel
        int listLeft = x + MODULE_LIST_X - 2;
        int listTop = y + MODULE_LIST_Y - 2;
        int listRight = listLeft + MODULE_LIST_WIDTH + 4;
        int listBottom = listTop + ROW_HEIGHT * VISIBLE_ROWS + 4;
        gfx.fill(listLeft, listTop, listRight, listBottom, BG_DEEP);
        gfx.fill(listLeft + 1, listTop + 1, listRight - 1, listBottom - 1, BG_PANEL);

        // Player inventory panel
        gfx.fill(x + 4, y + OverloadDeviceWorkbenchMenu.INVENTORY_Y - 12, x + imageWidth - 4, y + imageHeight - 4, BG_PANEL);

        // Slot frames
        renderSlotFrame(gfx, x + OverloadDeviceWorkbenchMenu.LEFT_COL_X - 1, y + OverloadDeviceWorkbenchMenu.DEVICE_Y - 1);
        for (int i = 0; i < menu.getStructuralSlotSpecs().size(); i++) {
            renderSlotFrame(gfx, x + OverloadDeviceWorkbenchMenu.LEFT_COL_X - 1,
                    y + OverloadDeviceWorkbenchMenu.STRUCTURAL_Y + i * OverloadDeviceWorkbenchMenu.STRUCTURAL_SPACING - 1);
        }
        renderSlotFrame(gfx, x + OverloadDeviceWorkbenchMenu.INPUT_X - 1, y + OverloadDeviceWorkbenchMenu.INPUT_Y - 1);

        renderStatusArea(gfx);
        renderInstallProgress(gfx);
        renderModuleList(gfx, mouseX, mouseY);
        renderEnergyBar(gfx);
        renderOverloadBar(gfx);
    }

    private void renderStatusArea(GuiGraphics gfx) {
        int x = leftPos + STATUS_X;
        int y = topPos + STATUS_Y;

        if (!menu.hasDeviceInserted()) {
            gfx.drawString(font, Component.translatable("ae2lt.overload_device_workbench.status.no_device"),
                    x, y + 4, TEXT_SECONDARY, false);
            return;
        }

        // Device name
        gfx.drawString(font, menu.getStatusText(), x, y, TEXT_PRIMARY, false);

        // Grid status
        boolean grid = menu.gridConnected != 0;
        String gridText = grid ? "Grid: ✓ 已连接" : "Grid: ✗ 未连接";
        int gridColor = grid ? GRID_ONLINE : GRID_OFFLINE;
        gfx.drawString(font, Component.literal(gridText), x, y + 12, gridColor, false);

        // Structural info
        if (menu.hasCoreInstalled()) {
            String coreInfo = "模块槽:" + menu.moduleTypeCount + "  cap:" + menu.baseOverload;
            gfx.drawString(font, Component.literal(coreInfo), x, y + 24, TEXT_SECONDARY, false);
        } else {
            gfx.drawString(font, Component.translatable("ae2lt.overload_armor.status.missing_core"),
                    x, y + 24, GRID_OFFLINE, false);
        }
    }

    private void renderInstallProgress(GuiGraphics gfx) {
        if (menu.installProgress <= 0) return;

        int barX = leftPos + OverloadDeviceWorkbenchMenu.INPUT_X + 20;
        int barY = topPos + OverloadDeviceWorkbenchMenu.INPUT_Y + 5;
        int barW = PROGRESS_BAR_WIDTH;
        int barH = PROGRESS_BAR_HEIGHT;

        gfx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, SLOT_BORDER_DARK);
        gfx.fill(barX, barY, barX + barW, barY + barH, PROGRESS_BG);

        double ratio = (double) menu.installProgress / OverloadDeviceWorkbenchMenu.INSTALL_TICKS;
        int filled = (int) (ratio * barW);
        if (filled > 0) {
            gfx.fill(barX, barY, barX + filled, barY + barH, HIGHLIGHT_GOLD);
        }

        String text = "装填中...";
        gfx.drawString(font, Component.literal(text), barX + barW + 4, barY - 1, TEXT_SECONDARY, false);
    }

    private void renderModuleList(GuiGraphics gfx, int mouseX, int mouseY) {
        var modules = menu.getInstalledModuleList();
        int listLeft = leftPos + MODULE_LIST_X;
        int listTop = topPos + MODULE_LIST_Y;
        int listRight = listLeft + MODULE_LIST_WIDTH;

        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, modules.size() - VISIBLE_ROWS)));

        String header = "模块 (" + modules.size() + ")";
        gfx.drawString(font, Component.literal(header), listLeft, listTop - 10, TEXT_PRIMARY, false);

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int modIndex = scrollOffset + i;
            int rowY = listTop + i * ROW_HEIGHT;
            if (modIndex >= modules.size()) {
                gfx.fill(listLeft, rowY, listRight, rowY + ROW_HEIGHT - 2, BG_DEEP);
                continue;
            }
            var stack = modules.get(modIndex);
            boolean hovered = mouseX >= listLeft && mouseX < listRight
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
            gfx.fill(listLeft, rowY, listRight, rowY + ROW_HEIGHT - 2,
                    hovered ? 0xFF3A3A3A : 0xFF2B2B2B);

            gfx.renderItem(stack, listLeft + 2, rowY + 1);
            String name = stack.getHoverName().getString();
            int cap = menu.getModuleMaxInstallAmount(stack);
            String amount = cap > 0 ? "×" + stack.getCount() + "/" + cap : "×" + stack.getCount();
            int amountWidth = font.width(amount);
            int nameX = listLeft + 22;
            int nameMaxWidth = listRight - nameX - (REMOVE_BUTTON_SIZE + REMOVE_BUTTON_MARGIN + amountWidth + 8);
            String truncated = truncate(font, name, nameMaxWidth);
            gfx.drawString(font, truncated, nameX, rowY + 6, TEXT_PRIMARY, false);
            int amountX = listRight - (REMOVE_BUTTON_SIZE + REMOVE_BUTTON_MARGIN + amountWidth + 4);
            gfx.drawString(font, amount, amountX, rowY + 6, HIGHLIGHT_GOLD, false);

            // Remove button
            int btnX = listRight - REMOVE_BUTTON_SIZE - REMOVE_BUTTON_MARGIN;
            int btnY = rowY + (ROW_HEIGHT - 2 - REMOVE_BUTTON_SIZE) / 2;
            boolean btnHovered = mouseX >= btnX && mouseX < btnX + REMOVE_BUTTON_SIZE
                    && mouseY >= btnY && mouseY < btnY + REMOVE_BUTTON_SIZE;
            gfx.fill(btnX, btnY, btnX + REMOVE_BUTTON_SIZE, btnY + REMOVE_BUTTON_SIZE,
                    btnHovered ? REMOVE_RED_HOVER : REMOVE_RED);
            gfx.drawString(font, "X", btnX + 3, btnY + 2, 0xFFFFFFFF, false);
        }

        // Scroll indicator
        if (modules.size() > VISIBLE_ROWS) {
            int barX = listRight + 2;
            int barTop = listTop;
            int barHeight = ROW_HEIGHT * VISIBLE_ROWS;
            gfx.fill(barX, barTop, barX + 4, barTop + barHeight, 0xFF151515);
            int thumbHeight = Math.max(8, barHeight * VISIBLE_ROWS / modules.size());
            int thumbY = barTop + (barHeight - thumbHeight) * scrollOffset / Math.max(1, modules.size() - VISIBLE_ROWS);
            gfx.fill(barX, thumbY, barX + 4, thumbY + thumbHeight, 0xFF888888);
        }
    }

    private void renderEnergyBar(GuiGraphics gfx) {
        if (menu.energyCapacity <= 0L) return;
        int barX = leftPos + MODULE_LIST_X;
        int barY = topPos + MODULE_LIST_Y + ROW_HEIGHT * VISIBLE_ROWS + 6;
        int barW = MODULE_LIST_WIDTH;

        gfx.drawString(font, Component.literal("能量"), barX, barY - 1, TEXT_PRIMARY, false);
        int bx = barX + 24;
        int bw = barW - 24;
        gfx.fill(bx - 1, barY - 1, bx + bw + 1, barY + BAR_HEIGHT + 1, SLOT_BORDER_DARK);
        gfx.fill(bx, barY, bx + bw, barY + BAR_HEIGHT, 0xFF1F2A22);
        double ratio = Math.min(1.0, (double) menu.energyStored / menu.energyCapacity);
        int filled = (int) (ratio * bw);
        if (filled > 0) {
            gfx.fill(bx, barY, bx + filled, barY + BAR_HEIGHT, ENERGY_GREEN);
        }
        String text = formatEnergy(menu.energyStored) + "/" + formatEnergy(menu.energyCapacity) + " FE";
        gfx.drawString(font, Component.literal(text), bx + bw + 4, barY - 1, TEXT_SECONDARY, false);
    }

    private void renderOverloadBar(GuiGraphics gfx) {
        if (menu.baseOverload <= 0) return;
        int barX = leftPos + MODULE_LIST_X;
        int barY = topPos + MODULE_LIST_Y + ROW_HEIGHT * VISIBLE_ROWS + 16;
        int barW = MODULE_LIST_WIDTH;

        gfx.drawString(font, Component.literal("负载"), barX, barY - 1, TEXT_PRIMARY, false);
        int bx = barX + 24;
        int bw = barW - 24;
        gfx.fill(bx - 1, barY - 1, bx + bw + 1, barY + BAR_HEIGHT + 1, SLOT_BORDER_DARK);
        gfx.fill(bx, barY, bx + bw, barY + BAR_HEIGHT, PROGRESS_BG);
        double ratio = Math.min(1.0, (double) menu.moduleLoadUsed / menu.baseOverload);
        int filled = (int) (ratio * bw);
        if (filled > 0) {
            gfx.fill(bx, barY, bx + filled, barY + BAR_HEIGHT, HIGHLIGHT_GOLD);
        }
        String text = menu.moduleLoadUsed + "/" + menu.baseOverload;
        gfx.drawString(font, Component.literal(text), bx + bw + 4, barY - 1, TEXT_SECONDARY, false);
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
    }

    private static String formatEnergy(long value) {
        if (value >= 1_000_000_000) return String.format(java.util.Locale.ROOT, "%.1fG", value / 1_000_000_000.0);
        if (value >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000.0);
        return String.valueOf(value);
    }

    private static void renderSlotFrame(GuiGraphics gfx, int x, int y) {
        gfx.fill(x, y, x + 18, y + 18, SLOT_BORDER_DARK);
        gfx.fill(x + 1, y + 1, x + 17, y + 17, SLOT_BORDER_LIGHT);
        gfx.fill(x + 2, y + 2, x + 16, y + 16, SLOT_BG);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
        renderModuleRowTooltip(gfx, mouseX, mouseY);
        renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(font, Component.translatable("block.ae2lt.overload_device_workbench"),
                8, 4, TEXT_PRIMARY, false);
        gfx.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_PRIMARY, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handleModuleListClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleModuleListClick(double mouseX, double mouseY) {
        var modules = menu.getInstalledModuleList();
        int listLeft = leftPos + MODULE_LIST_X;
        int listTop = topPos + MODULE_LIST_Y;
        int listRight = listLeft + MODULE_LIST_WIDTH;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int modIndex = scrollOffset + i;
            if (modIndex >= modules.size()) break;
            int rowY = listTop + i * ROW_HEIGHT;
            int btnX = listRight - REMOVE_BUTTON_SIZE - REMOVE_BUTTON_MARGIN;
            int btnY = rowY + (ROW_HEIGHT - 2 - REMOVE_BUTTON_SIZE) / 2;
            if (mouseX >= btnX && mouseX < btnX + REMOVE_BUTTON_SIZE
                    && mouseY >= btnY && mouseY < btnY + REMOVE_BUTTON_SIZE) {
                menu.requestUninstall(modIndex, hasShiftDown());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        var modules = menu.getInstalledModuleList();
        int listLeft = leftPos + MODULE_LIST_X;
        int listTop = topPos + MODULE_LIST_Y;
        int listRight = listLeft + MODULE_LIST_WIDTH;
        int listBottom = listTop + ROW_HEIGHT * VISIBLE_ROWS;
        if (mouseX >= listLeft && mouseX < listRight + 8
                && mouseY >= listTop && mouseY < listBottom) {
            int max = Math.max(0, modules.size() - VISIBLE_ROWS);
            if (scrollY > 0) scrollOffset = Math.max(0, scrollOffset - 1);
            else if (scrollY < 0) scrollOffset = Math.min(max, scrollOffset + 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void renderModuleRowTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        List<ItemStack> modules = menu.getInstalledModuleList();
        int listLeft = leftPos + MODULE_LIST_X;
        int listTop = topPos + MODULE_LIST_Y;
        int listRight = listLeft + MODULE_LIST_WIDTH;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int modIndex = scrollOffset + i;
            if (modIndex >= modules.size()) break;
            int rowY = listTop + i * ROW_HEIGHT;
            if (mouseX >= listLeft && mouseX < listRight - REMOVE_BUTTON_SIZE - REMOVE_BUTTON_MARGIN
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2) {
                gfx.renderTooltip(font, modules.get(modIndex), mouseX, mouseY);
                return;
            }
            int btnX = listRight - REMOVE_BUTTON_SIZE - REMOVE_BUTTON_MARGIN;
            int btnY = rowY + (ROW_HEIGHT - 2 - REMOVE_BUTTON_SIZE) / 2;
            if (mouseX >= btnX && mouseX < btnX + REMOVE_BUTTON_SIZE
                    && mouseY >= btnY && mouseY < btnY + REMOVE_BUTTON_SIZE) {
                gfx.renderComponentTooltip(font, List.of(
                        Component.translatable("ae2lt.overload_device_workbench.screen.uninstall_one"),
                        Component.translatable("ae2lt.overload_device_workbench.screen.uninstall_all")),
                        mouseX, mouseY);
                return;
            }
        }
    }

}
