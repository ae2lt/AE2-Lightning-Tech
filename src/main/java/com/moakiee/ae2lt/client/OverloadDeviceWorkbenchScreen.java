package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.menu.OverloadDeviceWorkbenchMenu;

/**
 * Workbench screen. Left column = armor + core/buffer/terminal (vertical 1+3). Right side = a
 * scrollable list of installed module types with an X button per row to pop one instance back to
 * the player. Beneath the list sits a single "install" slot where the player drops submodule
 * items; the server auto-installs them one at a time while the idle-overload budget allows.
 */
public class OverloadDeviceWorkbenchScreen extends AbstractContainerScreen<OverloadDeviceWorkbenchMenu> {
    private static final Component SCREEN_TITLE = Component.translatable("block.ae2lt.overload_device_workbench");

    private static final int ROW_HEIGHT = 20;
    private static final int REMOVE_BUTTON_SIZE = 12;
    private static final int REMOVE_BUTTON_MARGIN = 4;
    private static final int VISIBLE_ROWS = 3;

    private int scrollOffset = 0;

    public OverloadDeviceWorkbenchScreen(OverloadDeviceWorkbenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 198;
        this.imageHeight = 208;
        this.inventoryLabelX = OverloadDeviceWorkbenchMenu.INVENTORY_X;
        this.inventoryLabelY = OverloadDeviceWorkbenchMenu.INVENTORY_Y - 10;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Dark plate background.
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF1E1E1E);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF313131);

        // Left column (device + structural slots) panel.
        graphics.fill(leftPos + 4, topPos + 14, leftPos + 30, topPos + 108, 0xFF1F1F1F);

        // Module list panel.
        int listLeft = leftPos + OverloadDeviceWorkbenchMenu.LIST_X - 2;
        int listTop = topPos + OverloadDeviceWorkbenchMenu.LIST_Y - 2;
        int listRight = listLeft + OverloadDeviceWorkbenchMenu.LIST_WIDTH + 4;
        int listBottom = listTop + OverloadDeviceWorkbenchMenu.LIST_HEIGHT + 4;
        graphics.fill(listLeft, listTop, listRight, listBottom, 0xFF1B1B1B);
        graphics.fill(listLeft + 1, listTop + 1, listRight - 1, listBottom - 1, 0xFF262626);

        // Player inventory divider.
        graphics.fill(leftPos + 4,
                topPos + OverloadDeviceWorkbenchMenu.INVENTORY_Y - 12,
                leftPos + imageWidth - 4,
                topPos + imageHeight - 4,
                0xFF262626);

        // Slot frames for the left column.
        renderSlotFrame(graphics, leftPos + OverloadDeviceWorkbenchMenu.LEFT_COL_X - 1,
                topPos + OverloadDeviceWorkbenchMenu.DEVICE_Y - 1);
        for (int index = 0; index < menu.getStructuralSlotSpecs().size(); index++) {
            renderSlotFrame(
                    graphics,
                    leftPos + OverloadDeviceWorkbenchMenu.LEFT_COL_X - 1,
                    topPos + OverloadDeviceWorkbenchMenu.STRUCTURAL_Y
                            + index * OverloadDeviceWorkbenchMenu.STRUCTURAL_SPACING - 1);
        }

        // Input slot frame.
        renderSlotFrame(graphics, leftPos + OverloadDeviceWorkbenchMenu.INPUT_X - 1,
                topPos + OverloadDeviceWorkbenchMenu.INPUT_Y - 1);

        renderModuleList(graphics, mouseX, mouseY);
    }

    private void renderModuleList(GuiGraphics graphics, int mouseX, int mouseY) {
        var modules = menu.getInstalledModuleList();
        int listLeft = leftPos + OverloadDeviceWorkbenchMenu.LIST_X;
        int listTop = topPos + OverloadDeviceWorkbenchMenu.LIST_Y;
        int listRight = listLeft + OverloadDeviceWorkbenchMenu.LIST_WIDTH;

        scrollOffset = Math.max(0, Math.min(scrollOffset,
                Math.max(0, modules.size() - VISIBLE_ROWS)));

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int modIndex = scrollOffset + i;
            int rowY = listTop + i * ROW_HEIGHT;
            if (modIndex >= modules.size()) {
                graphics.fill(listLeft, rowY, listRight, rowY + ROW_HEIGHT - 2, 0xFF1A1A1A);
                continue;
            }
            var stack = modules.get(modIndex);
            boolean hovered = mouseX >= listLeft && mouseX < listRight
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
            graphics.fill(listLeft, rowY, listRight, rowY + ROW_HEIGHT - 2,
                    hovered ? 0xFF3A3A3A : 0xFF2B2B2B);

            // Module icon + name + ×N (or ×N/Max when the submodule declares a per-type cap)
            graphics.renderItem(stack, listLeft + 2, rowY + 1);
            String name = stack.getHoverName().getString();
            int cap = menu.getModuleMaxInstallAmount(stack);
            String amount = cap > 0
                    ? "×" + stack.getCount() + "/" + cap
                    : "×" + stack.getCount();
            int textColor = 0xE0E0E0;
            int nameX = listLeft + 22;
            int amountWidth = font.width(amount);
            int nameMaxWidth = listRight - nameX - (REMOVE_BUTTON_SIZE + REMOVE_BUTTON_MARGIN + amountWidth + 8);
            String truncated = truncate(font, name, nameMaxWidth);
            graphics.drawString(font, truncated, nameX, rowY + 6, textColor, false);
            int amountX = listRight - (REMOVE_BUTTON_SIZE + REMOVE_BUTTON_MARGIN + amountWidth + 4);
            graphics.drawString(font, amount, amountX, rowY + 6, 0xF6D365, false);

            // Remove (X) button
            int btnX = listRight - REMOVE_BUTTON_SIZE - REMOVE_BUTTON_MARGIN;
            int btnY = rowY + (ROW_HEIGHT - 2 - REMOVE_BUTTON_SIZE) / 2;
            boolean btnHovered = mouseX >= btnX && mouseX < btnX + REMOVE_BUTTON_SIZE
                    && mouseY >= btnY && mouseY < btnY + REMOVE_BUTTON_SIZE;
            graphics.fill(btnX, btnY, btnX + REMOVE_BUTTON_SIZE, btnY + REMOVE_BUTTON_SIZE,
                    btnHovered ? 0xFFC24848 : 0xFF7A2A2A);
            graphics.drawString(font, "X", btnX + 3, btnY + 2, 0xFFFFFFFF, false);
        }

        // Scroll indicator
        if (modules.size() > VISIBLE_ROWS) {
            int barX = listRight + 2;
            int barTop = listTop;
            int barHeight = ROW_HEIGHT * VISIBLE_ROWS;
            graphics.fill(barX, barTop, barX + 4, barTop + barHeight, 0xFF151515);
            int thumbHeight = Math.max(8, barHeight * VISIBLE_ROWS / modules.size());
            int thumbY = barTop + (barHeight - thumbHeight) * scrollOffset
                    / Math.max(1, modules.size() - VISIBLE_ROWS);
            graphics.fill(barX, thumbY, barX + 4, thumbY + thumbHeight, 0xFF888888);
        }
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
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
        int listLeft = leftPos + OverloadDeviceWorkbenchMenu.LIST_X;
        int listTop = topPos + OverloadDeviceWorkbenchMenu.LIST_Y;
        int listRight = listLeft + OverloadDeviceWorkbenchMenu.LIST_WIDTH;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int modIndex = scrollOffset + i;
            if (modIndex >= modules.size()) break;
            int rowY = listTop + i * ROW_HEIGHT;
            int btnX = listRight - REMOVE_BUTTON_SIZE - REMOVE_BUTTON_MARGIN;
            int btnY = rowY + (ROW_HEIGHT - 2 - REMOVE_BUTTON_SIZE) / 2;
            if (mouseX >= btnX && mouseX < btnX + REMOVE_BUTTON_SIZE
                    && mouseY >= btnY && mouseY < btnY + REMOVE_BUTTON_SIZE) {
                // Shift-click = uninstall entire entry in one shot; plain click = pop one
                // instance. Matches the vanilla/Sophisticated-Storage mental model for "all vs
                // one" shift semantics on a list row.
                menu.requestUninstall(modIndex, hasShiftDown());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        var modules = menu.getInstalledModuleList();
        int listLeft = leftPos + OverloadDeviceWorkbenchMenu.LIST_X;
        int listTop = topPos + OverloadDeviceWorkbenchMenu.LIST_Y;
        int listRight = listLeft + OverloadDeviceWorkbenchMenu.LIST_WIDTH;
        int listBottom = listTop + ROW_HEIGHT * VISIBLE_ROWS;
        if (mouseX >= listLeft && mouseX < listRight + 8
                && mouseY >= listTop && mouseY < listBottom) {
            int max = Math.max(0, modules.size() - VISIBLE_ROWS);
            if (scrollY > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else if (scrollY < 0) {
                scrollOffset = Math.min(max, scrollOffset + 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, SCREEN_TITLE, 8, 6, 0xE0E0E0, false);
        graphics.drawString(font, menu.getStatusText(),
                OverloadDeviceWorkbenchMenu.LIST_X,
                OverloadDeviceWorkbenchMenu.LIST_Y + ROW_HEIGHT * VISIBLE_ROWS + 2,
                0xF6D365, false);
        graphics.drawString(font,
                Component.translatable("ae2lt.overload_device_workbench.screen.modules",
                        menu.moduleTypeCount, menu.moduleIdleUsed, menu.baseOverload),
                OverloadDeviceWorkbenchMenu.LIST_X,
                OverloadDeviceWorkbenchMenu.LIST_Y + ROW_HEIGHT * VISIBLE_ROWS + 12,
                0xE0E0E0,
                false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xE0E0E0, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderModuleRowTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderModuleRowTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ItemStack> modules = menu.getInstalledModuleList();
        int listLeft = leftPos + OverloadDeviceWorkbenchMenu.LIST_X;
        int listTop = topPos + OverloadDeviceWorkbenchMenu.LIST_Y;
        int listRight = listLeft + OverloadDeviceWorkbenchMenu.LIST_WIDTH;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int modIndex = scrollOffset + i;
            if (modIndex >= modules.size()) break;
            int rowY = listTop + i * ROW_HEIGHT;
            if (mouseX >= listLeft && mouseX < listRight - REMOVE_BUTTON_SIZE - REMOVE_BUTTON_MARGIN
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2) {
                graphics.renderTooltip(font, modules.get(modIndex), mouseX, mouseY);
                return;
            }
            int btnX = listRight - REMOVE_BUTTON_SIZE - REMOVE_BUTTON_MARGIN;
            int btnY = rowY + (ROW_HEIGHT - 2 - REMOVE_BUTTON_SIZE) / 2;
            if (mouseX >= btnX && mouseX < btnX + REMOVE_BUTTON_SIZE
                    && mouseY >= btnY && mouseY < btnY + REMOVE_BUTTON_SIZE) {
                graphics.renderComponentTooltip(font,
                        List.of(
                                Component.translatable(
                                        "ae2lt.overload_device_workbench.screen.uninstall_one"),
                                Component.translatable(
                                        "ae2lt.overload_device_workbench.screen.uninstall_all")),
                        mouseX, mouseY);
                return;
            }
        }
    }

    private static void renderSlotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF080808);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        graphics.fill(x + 2, y + 2, x + 16, y + 16, 0xFF1B1B1B);
    }
}
