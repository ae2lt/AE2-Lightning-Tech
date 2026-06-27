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

    private static final int TEXTURE_WIDTH = 320;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 245;

    private static final int TEXT_ON_LIGHT_BG = 0xFF6E748C;
    private static final int TEXT_ON_DARK_BG = 0xFFFFFFFF;

    private static final int STATUS_X = 44;
    private static final int STATUS_Y = 22;
    private static final int STATUS_SECOND_LINE_Y = 14;

    private static final int MODULE_HEADER_X = 42;
    private static final int MODULE_HEADER_Y = 49;
    private static final int MODULE_ROW_X = 42;
    private static final int MODULE_ROW_Y = 60;
    private static final int MODULE_ROW_WIDTH = 118;
    private static final int MODULE_ROW_HEIGHT = 17;
    private static final int MODULE_ICON_X = 44;
    private static final int MODULE_NAME_X = 67;
    private static final int MODULE_ITEM_Y_OFFSET = 1;
    private static final int MODULE_TEXT_Y_OFFSET = 5;
    private static final int VISIBLE_ROWS = 5;
    private static final int MODULE_ROW_SRC_X = 180;
    private static final int MODULE_ROW_SRC_Y = 90;
    private static final int MODULE_ROW_SELECTED_SRC_Y = 111;

    private static final int REMOVE_BUTTON_SIZE = 10;
    private static final int REMOVE_BUTTON_X = 148;
    private static final int REMOVE_BUTTON_Y_OFFSET = 3;
    private static final int REMOVE_BUTTON_SRC_X = 191;
    private static final int REMOVE_BUTTON_SRC_Y = 5;
    private static final int REMOVE_BUTTON_HOVER_SRC_X = 202;
    private static final int REMOVE_BUTTON_HOVER_SRC_Y = 6;
    private static final int REMOVE_BUTTON_WIDTH = 9;
    private static final int REMOVE_BUTTON_HEIGHT = 10;
    private static final int REMOVE_BUTTON_HOVER_HEIGHT = 9;

    private static final int SCROLLBAR_X = 164;
    private static final int SCROLLBAR_Y = 61;
    private static final int SCROLLBAR_WIDTH = 7;
    private static final int SCROLLBAR_HEIGHT = 82;
    private static final int SCROLLBAR_THUMB_SRC_X = 180;
    private static final int SCROLLBAR_THUMB_SRC_Y = 0;
    private static final int SCROLLBAR_THUMB_HOVER_SRC_Y = 17;
    private static final int SCROLLBAR_THUMB_HEIGHT = 15;
    private static final int SCROLLBAR_THUMB_HOVER_HEIGHT = 14;

    private static final int ARROW_PROGRESS_X = 8;
    private static final int ARROW_PROGRESS_Y = 97;
    private static final int ARROW_PROGRESS_WIDTH = 28;
    private static final int ARROW_PROGRESS_HEIGHT = 38;
    private static final int ARROW_PROGRESS_SRC_X = 180;
    private static final int ARROW_PROGRESS_SRC_Y = 48;
    private static final int ARROW_PROGRESS_VISIBLE_OFFSET_X = 9;
    private static final int ARROW_PROGRESS_VISIBLE_WIDTH = 16;

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
        gfx.blit(TEXTURE, leftPos, topPos, 0, 0, GUI_WIDTH, GUI_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        renderStatusArea(gfx);
        renderInstallProgress(gfx);
        renderModuleList(gfx, mouseX, mouseY);
    }

    private void renderStatusArea(GuiGraphics gfx) {
        int x = leftPos + STATUS_X;
        int y = topPos + STATUS_Y;

        if (!menu.hasDeviceInserted()) {
            gfx.drawString(font, Component.translatable("ae2lt.overload_device_workbench.status.no_device"),
                    x, y + 7, TEXT_ON_DARK_BG, false);
            return;
        }

        gfx.drawString(font, menu.getStatusText(), x, y, TEXT_ON_DARK_BG, false);

        boolean grid = menu.gridConnected != 0;
        Component gridText = grid
                ? Component.translatable("ae2lt.overload_device_workbench.screen.network.online")
                : Component.translatable("ae2lt.overload_device_workbench.screen.network.offline");
        gfx.drawString(font, gridText, x, y + STATUS_SECOND_LINE_Y, TEXT_ON_DARK_BG, false);
    }

    private void renderInstallProgress(GuiGraphics gfx) {
        if (menu.installProgress <= 0) {
            return;
        }

        double ratio = (double) menu.installProgress / OverloadDeviceWorkbenchMenu.INSTALL_TICKS;
        int visible = Math.max(1, (int) Math.ceil(ARROW_PROGRESS_VISIBLE_WIDTH * ratio));
        int filled = Math.min(ARROW_PROGRESS_WIDTH, ARROW_PROGRESS_VISIBLE_OFFSET_X + visible);
        gfx.blit(TEXTURE, leftPos + ARROW_PROGRESS_X, topPos + ARROW_PROGRESS_Y,
                ARROW_PROGRESS_SRC_X, ARROW_PROGRESS_SRC_Y,
                filled, ARROW_PROGRESS_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private void renderModuleList(GuiGraphics gfx, int mouseX, int mouseY) {
        List<ItemStack> modules = menu.getInstalledModuleList();
        int listLeft = leftPos + MODULE_ROW_X;
        int listTop = topPos + MODULE_ROW_Y;
        int listRight = listLeft + MODULE_ROW_WIDTH;

        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, modules.size() - VISIBLE_ROWS)));

        Component header = Component.translatable(
                "ae2lt.overload_device_workbench.screen.module_types",
                modules.size());
        gfx.drawString(font, header, leftPos + MODULE_HEADER_X, topPos + MODULE_HEADER_Y, TEXT_ON_LIGHT_BG, false);

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
            renderModuleRowFrame(gfx, rowY, hovered);

            ItemStack stack = modules.get(moduleIndex);
            gfx.renderItem(stack, leftPos + MODULE_ICON_X, rowY + MODULE_ITEM_Y_OFFSET);

            int cap = menu.getModuleMaxInstallAmount(stack);
            String amount = cap > 0 ? "x" + stack.getCount() + "/" + cap : "x" + stack.getCount();
            int amountWidth = font.width(amount);
            int amountX = REMOVE_BUTTON_X - amountWidth - 4;
            int rowTextColor = hovered ? TEXT_ON_LIGHT_BG : TEXT_ON_DARK_BG;
            gfx.drawString(font, Component.literal(amount),
                    leftPos + amountX, rowY + MODULE_TEXT_Y_OFFSET, rowTextColor, false);

            int nameX = leftPos + MODULE_NAME_X;
            int nameMaxWidth = leftPos + amountX - nameX - 3;
            gfx.drawString(font, Component.literal(truncate(font, stack.getHoverName().getString(), nameMaxWidth)),
                    nameX, rowY + MODULE_TEXT_Y_OFFSET, rowTextColor, false);

            renderRemoveButton(gfx, leftPos + REMOVE_BUTTON_X, rowY + REMOVE_BUTTON_Y_OFFSET, mouseX, mouseY);
        }

        if (modules.size() > VISIBLE_ROWS) {
            renderScrollBar(gfx, modules.size(), mouseX, mouseY);
        }
    }

    private void renderModuleRowFrame(GuiGraphics gfx, int rowY, boolean selected) {
        gfx.blit(TEXTURE, leftPos + MODULE_ROW_X, rowY,
                MODULE_ROW_SRC_X, selected ? MODULE_ROW_SELECTED_SRC_Y : MODULE_ROW_SRC_Y,
                MODULE_ROW_WIDTH, MODULE_ROW_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private void renderRemoveButton(GuiGraphics gfx, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x
                && mouseX < x + REMOVE_BUTTON_SIZE
                && mouseY >= y
                && mouseY < y + REMOVE_BUTTON_SIZE;
        gfx.blit(TEXTURE, x, y,
                hovered ? REMOVE_BUTTON_HOVER_SRC_X : REMOVE_BUTTON_SRC_X,
                hovered ? REMOVE_BUTTON_HOVER_SRC_Y : REMOVE_BUTTON_SRC_Y,
                REMOVE_BUTTON_WIDTH,
                hovered ? REMOVE_BUTTON_HOVER_HEIGHT : REMOVE_BUTTON_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private void renderScrollBar(GuiGraphics gfx, int moduleCount, int mouseX, int mouseY) {
        int barX = leftPos + SCROLLBAR_X;
        int barTop = topPos + SCROLLBAR_Y;
        int thumbSpace = SCROLLBAR_HEIGHT - SCROLLBAR_THUMB_HEIGHT;
        int thumbY = barTop + thumbSpace * scrollOffset / Math.max(1, moduleCount - VISIBLE_ROWS);
        boolean hovered = mouseX >= barX
                && mouseX < barX + SCROLLBAR_WIDTH
                && mouseY >= thumbY
                && mouseY < thumbY + SCROLLBAR_THUMB_HEIGHT;
        gfx.blit(TEXTURE, barX, thumbY,
                SCROLLBAR_THUMB_SRC_X,
                hovered ? SCROLLBAR_THUMB_HOVER_SRC_Y : SCROLLBAR_THUMB_SRC_Y,
                SCROLLBAR_WIDTH,
                hovered ? SCROLLBAR_THUMB_HOVER_HEIGHT : SCROLLBAR_THUMB_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);
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
                42, 6, TEXT_ON_LIGHT_BG, false);
        gfx.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_ON_LIGHT_BG, false);
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
            int rowY = topPos + MODULE_ROW_Y + row * MODULE_ROW_HEIGHT + REMOVE_BUTTON_Y_OFFSET;
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
        int listLeft = leftPos + MODULE_ROW_X;
        int listTop = topPos + MODULE_ROW_Y;
        int listRight = leftPos + SCROLLBAR_X + SCROLLBAR_WIDTH;
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
        int listLeft = leftPos + MODULE_ROW_X;
        int listTop = topPos + MODULE_ROW_Y;
        int listRight = listLeft + MODULE_ROW_WIDTH;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int moduleIndex = scrollOffset + row;
            if (moduleIndex >= modules.size()) {
                break;
            }
            int rowY = listTop + row * MODULE_ROW_HEIGHT;
            int buttonX = leftPos + REMOVE_BUTTON_X;
            int buttonY = rowY + REMOVE_BUTTON_Y_OFFSET;
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
