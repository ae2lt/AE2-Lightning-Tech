package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.OverloadDeviceWorkbenchMenu;

public class OverloadDeviceWorkbenchScreen extends AbstractContainerScreen<OverloadDeviceWorkbenchMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/overload_workplace_gui.png");

    private static final int TEXTURE_SIZE = 256;
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 245;

    private static final int TEXT_PRIMARY = 0xFF6E748C;
    private static final int TEXT_SECONDARY = 0xFF7D839B;
    private static final int TEXT_OK = 0xFF5F8F78;
    private static final int TEXT_WARN = 0xFF9A8064;
    private static final int TEXT_ACCENT = 0xFF9A8A5B;
    private static final int ROW_HOVER = 0x304D4D67;
    private static final int REMOVE_RED = 0xFF87545E;
    private static final int REMOVE_RED_HOVER = 0xFFA96A72;
    private static final int REMOVE_TEXT = 0xFFCCD2DE;
    private static final int SCROLL_TRACK = 0x80373B72;
    private static final int SCROLL_THUMB = 0xFFADB0C4;

    private static final int STATUS_X = 44;
    private static final int STATUS_Y = 22;
    private static final int STATUS_SECOND_LINE_Y = 14;

    private static final int MODULE_HEADER_X = 42;
    private static final int MODULE_HEADER_Y = 49;
    private static final int MODULE_LIST_X = 42;
    private static final int MODULE_CONTENT_X = 54;
    private static final int MODULE_LIST_Y = 60;
    private static final int MODULE_LIST_WIDTH = 125;
    private static final int MODULE_CONTENT_WIDTH = 112;
    private static final int MODULE_ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 4;

    private static final int REMOVE_BUTTON_SIZE = 10;
    private static final int REMOVE_BUTTON_X = 155;

    private static final int PROGRESS_X = 43;
    private static final int PROGRESS_Y = 143;
    private static final int PROGRESS_WIDTH = 123;
    private static final int PROGRESS_HEIGHT = 6;
    private static final int PROGRESS_SRC_X = 180;
    private static final int PROGRESS_SRC_Y = 35;
    private static final int PROGRESS_SRC_WIDTH = 72;

    private int scrollOffset = 0;

    public OverloadDeviceWorkbenchScreen(OverloadDeviceWorkbenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelX = OverloadDeviceWorkbenchMenu.INVENTORY_X;
        this.inventoryLabelY = OverloadDeviceWorkbenchMenu.INVENTORY_Y - 10;
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        gfx.blit(TEXTURE, leftPos, topPos, 0, 0, GUI_WIDTH, GUI_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        renderStatusArea(gfx);
        renderInstallProgress(gfx);
        renderModuleList(gfx, mouseX, mouseY);
    }

    private void renderStatusArea(GuiGraphics gfx) {
        int x = leftPos + STATUS_X;
        int y = topPos + STATUS_Y;

        if (!menu.hasDeviceInserted()) {
            gfx.drawString(font, Component.translatable("ae2lt.overload_device_workbench.status.no_device"),
                    x, y + 7, TEXT_SECONDARY, false);
            return;
        }

        gfx.drawString(font, menu.getStatusText(), x, y, TEXT_PRIMARY, false);

        boolean grid = menu.gridConnected != 0;
        Component gridText = grid
                ? Component.translatable("ae2lt.overload_device_workbench.screen.network.online")
                : Component.translatable("ae2lt.overload_device_workbench.screen.network.offline");
        gfx.drawString(font, gridText, x, y + STATUS_SECOND_LINE_Y, grid ? TEXT_OK : TEXT_WARN, false);
    }

    private void renderInstallProgress(GuiGraphics gfx) {
        if (menu.installProgress <= 0) {
            return;
        }

        double ratio = (double) menu.installProgress / OverloadDeviceWorkbenchMenu.INSTALL_TICKS;
        int filled = Math.min(PROGRESS_WIDTH, Math.max(0, (int) Math.round(PROGRESS_WIDTH * ratio)));
        int destX = leftPos + PROGRESS_X;
        int remaining = filled;
        while (remaining > 0) {
            int slice = Math.min(remaining, PROGRESS_SRC_WIDTH);
            gfx.blit(TEXTURE, destX, topPos + PROGRESS_Y,
                    PROGRESS_SRC_X, PROGRESS_SRC_Y,
                    slice, PROGRESS_HEIGHT,
                    TEXTURE_SIZE, TEXTURE_SIZE);
            remaining -= slice;
            destX += slice;
        }
    }

    private void renderModuleList(GuiGraphics gfx, int mouseX, int mouseY) {
        List<ItemStack> modules = menu.getInstalledModuleList();
        int listLeft = leftPos + MODULE_CONTENT_X;
        int listTop = topPos + MODULE_LIST_Y;
        int listRight = listLeft + MODULE_CONTENT_WIDTH;

        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, modules.size() - VISIBLE_ROWS)));

        Component header = menu.isRailgunDevice()
                ? Component.translatable("ae2lt.overload_device_workbench.screen.module_types", modules.size())
                : Component.translatable(
                        "ae2lt.overload_device_workbench.screen.module_units",
                        menu.moduleUnitCount,
                        menu.moduleSlotCount);
        gfx.drawString(font, header, leftPos + MODULE_HEADER_X, topPos + MODULE_HEADER_Y, TEXT_PRIMARY, false);

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int moduleIndex = scrollOffset + row;
            if (moduleIndex >= modules.size()) {
                continue;
            }

            int rowY = listTop + row * MODULE_ROW_HEIGHT;
            boolean hovered = mouseX >= listLeft
                    && mouseX < listRight
                    && mouseY >= rowY
                    && mouseY < rowY + MODULE_ROW_HEIGHT;
            if (hovered) {
                gfx.fill(listLeft, rowY, listRight, rowY + MODULE_ROW_HEIGHT - 1, ROW_HOVER);
            }

            ItemStack stack = modules.get(moduleIndex);
            gfx.renderItem(stack, listLeft + 2, rowY + 1);

            int cap = menu.getModuleMaxInstallAmount(stack);
            String amount = cap > 0 ? "x" + stack.getCount() + "/" + cap : "x" + stack.getCount();
            int amountWidth = font.width(amount);
            int amountX = REMOVE_BUTTON_X - amountWidth - 4;
            gfx.drawString(font, Component.literal(amount), leftPos + amountX, rowY + 5, TEXT_ACCENT, false);

            int nameX = listLeft + 21;
            int nameMaxWidth = leftPos + amountX - nameX - 3;
            gfx.drawString(font, Component.literal(truncate(font, stack.getHoverName().getString(), nameMaxWidth)),
                    nameX, rowY + 5, TEXT_PRIMARY, false);

            renderRemoveButton(gfx, leftPos + REMOVE_BUTTON_X, rowY + 4, mouseX, mouseY);
        }

        if (modules.size() > VISIBLE_ROWS) {
            renderScrollBar(gfx, modules.size());
        }
    }

    private void renderRemoveButton(GuiGraphics gfx, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x
                && mouseX < x + REMOVE_BUTTON_SIZE
                && mouseY >= y
                && mouseY < y + REMOVE_BUTTON_SIZE;
        gfx.fill(x, y, x + REMOVE_BUTTON_SIZE, y + REMOVE_BUTTON_SIZE,
                hovered ? REMOVE_RED_HOVER : REMOVE_RED);
        gfx.drawString(font, Component.literal("x"), x + 3, y + 1, REMOVE_TEXT, false);
    }

    private void renderScrollBar(GuiGraphics gfx, int moduleCount) {
        int barX = leftPos + MODULE_LIST_X - 6;
        int barTop = topPos + MODULE_LIST_Y + 1;
        int barHeight = MODULE_ROW_HEIGHT * VISIBLE_ROWS - 2;
        gfx.fill(barX, barTop, barX + 3, barTop + barHeight, SCROLL_TRACK);
        int thumbHeight = Math.max(10, barHeight * VISIBLE_ROWS / moduleCount);
        int thumbY = barTop + (barHeight - thumbHeight) * scrollOffset / Math.max(1, moduleCount - VISIBLE_ROWS);
        gfx.fill(barX, thumbY, barX + 3, thumbY + thumbHeight, SCROLL_THUMB);
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width("...");
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - ellipsisWidth)) + "...";
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
                42, 6, TEXT_PRIMARY, false);
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
        List<ItemStack> modules = menu.getInstalledModuleList();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int moduleIndex = scrollOffset + row;
            if (moduleIndex >= modules.size()) {
                break;
            }
            int rowY = topPos + MODULE_LIST_Y + row * MODULE_ROW_HEIGHT + 4;
            int buttonX = leftPos + REMOVE_BUTTON_X;
            if (mouseX >= buttonX
                    && mouseX < buttonX + REMOVE_BUTTON_SIZE
                    && mouseY >= rowY
                    && mouseY < rowY + REMOVE_BUTTON_SIZE) {
                menu.requestUninstall(moduleIndex, hasShiftDown());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int listLeft = leftPos + MODULE_LIST_X - 8;
        int listTop = topPos + MODULE_LIST_Y;
        int listRight = leftPos + MODULE_CONTENT_X + MODULE_CONTENT_WIDTH;
        int listBottom = listTop + MODULE_ROW_HEIGHT * VISIBLE_ROWS;
        if (mouseX >= listLeft
                && mouseX < listRight
                && mouseY >= listTop
                && mouseY < listBottom) {
            int max = Math.max(0, menu.getInstalledModuleList().size() - VISIBLE_ROWS);
            if (scrollY > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else if (scrollY < 0) {
                scrollOffset = Math.min(max, scrollOffset + 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void renderModuleRowTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        List<ItemStack> modules = menu.getInstalledModuleList();
        int listLeft = leftPos + MODULE_CONTENT_X;
        int listTop = topPos + MODULE_LIST_Y;
        int listRight = listLeft + MODULE_CONTENT_WIDTH;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int moduleIndex = scrollOffset + row;
            if (moduleIndex >= modules.size()) {
                break;
            }
            int rowY = listTop + row * MODULE_ROW_HEIGHT;
            int buttonX = leftPos + REMOVE_BUTTON_X;
            int buttonY = rowY + 4;
            if (mouseX >= buttonX
                    && mouseX < buttonX + REMOVE_BUTTON_SIZE
                    && mouseY >= buttonY
                    && mouseY < buttonY + REMOVE_BUTTON_SIZE) {
                gfx.renderComponentTooltip(font, List.of(
                                Component.translatable("ae2lt.overload_device_workbench.screen.uninstall_one"),
                                Component.translatable("ae2lt.overload_device_workbench.screen.uninstall_all")),
                        mouseX, mouseY);
                return;
            }
            if (mouseX >= listLeft
                    && mouseX < listRight
                    && mouseY >= rowY
                    && mouseY < rowY + MODULE_ROW_HEIGHT) {
                gfx.renderTooltip(font, modules.get(moduleIndex), mouseX, mouseY);
                return;
            }
        }
    }
}
