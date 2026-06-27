package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.menu.MatrixControllerMenu;
import com.moakiee.ae2lt.network.MatrixControllerActionPacket;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class MatrixControllerScreen extends AbstractContainerScreen<MatrixControllerMenu> {
    public MatrixControllerScreen(MatrixControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 196;
        imageHeight = 122;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 12;
        int y = topPos + 58;
        addRenderableWidget(actionButton(x, y, 82, "ae2lt.matrix.gui.scan", MatrixControllerActionPacket.Action.SCAN_FORM));
        addRenderableWidget(actionButton(x + 90, y, 82, "ae2lt.matrix.gui.build", MatrixControllerActionPacket.Action.AUTO_BUILD));
        addRenderableWidget(actionButton(x, y + 24, 82, "ae2lt.matrix.gui.upgrade", MatrixControllerActionPacket.Action.UPGRADE_PATTERN_STORAGE));
        addRenderableWidget(actionButton(x + 90, y + 24, 82, "ae2lt.matrix.gui.deform", MatrixControllerActionPacket.Action.DEFORM));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF20242A);
        guiGraphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xFF2D333A);
        guiGraphics.fill(leftPos + 8, topPos + 30, leftPos + imageWidth - 8, topPos + 52, 0xFF171A1F);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 10, 10, 0xE6EEF5, false);
        Component status = menu.isFormed()
                ? Component.translatable("ae2lt.matrix.gui.status_formed",
                        menu.getMemberCount(),
                        menu.getPatternStorageCount(),
                        menu.getCraftingUnitCount())
                : Component.translatable("ae2lt.matrix.gui.status_unformed");
        guiGraphics.drawString(font, status, 12, 36, menu.isFormed() ? 0x85F29E : 0xF2D37A, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private Button actionButton(int x, int y, int width, String key, MatrixControllerActionPacket.Action action) {
        return Button.builder(Component.translatable(key), button -> PacketDistributor.sendToServer(
                new MatrixControllerActionPacket(menu.token(), menu.getBlockPos(), action)))
                .bounds(x, y, width, 20)
                .build();
    }
}
