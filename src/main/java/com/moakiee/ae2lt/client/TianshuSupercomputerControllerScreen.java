package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanIssue;
import com.moakiee.ae2lt.menu.TianshuSupercomputerControllerMenu;
import com.moakiee.ae2lt.network.TianshuControllerActionPacket;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class TianshuSupercomputerControllerScreen
        extends AbstractContainerScreen<TianshuSupercomputerControllerMenu> {
    private Button fastPlanningButton;

    public TianshuSupercomputerControllerScreen(
            TianshuSupercomputerControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 220;
        imageHeight = 172;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(
                Component.translatable("ae2lt.tianshu.gui.build"),
                button -> PacketDistributor.sendToServer(
                        new TianshuControllerActionPacket(menu.token(), menu.getBlockPos(),
                                TianshuControllerActionPacket.Action.AUTO_BUILD)))
                .bounds(leftPos + 10, topPos + 140, 96, 20)
                .build());
        fastPlanningButton = addRenderableWidget(Button.builder(
                fastPlanningText(),
                button -> PacketDistributor.sendToServer(
                        new TianshuControllerActionPacket(menu.token(), menu.getBlockPos(),
                                TianshuControllerActionPacket.Action.TOGGLE_FAST_PLANNING)))
                .bounds(leftPos + 114, topPos + 140, 96, 20)
                .build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (fastPlanningButton != null) fastPlanningButton.setMessage(fastPlanningText());
    }

    private Component fastPlanningText() {
        return Component.translatable(menu.isFastPlanningEnabled()
                ? "ae2lt.tianshu.gui.fast_planning.on"
                : "ae2lt.tianshu.gui.fast_planning.off");
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF181D24);
        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xFF27313D);
        graphics.fill(leftPos + 10, topPos + 30, leftPos + imageWidth - 10, topPos + 58, 0xFF11161C);
        graphics.fill(leftPos + 10, topPos + 66, leftPos + imageWidth - 10, topPos + 132, 0xFF202832);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 11, 0xE8F4FF, false);
        int statusColor = menu.isFormed() ? 0x74F29A : 0xFF9D7A;
        graphics.drawString(font, Component.translatable(
                menu.isFormed() ? "ae2lt.tianshu.gui.formed" : "ae2lt.tianshu.gui.unformed"),
                14, 38, statusColor, false);
        if (!menu.isFormed()) {
            graphics.drawString(font, issueText(), 14, 72, 0xFFB8A8, false);
            return;
        }
        graphics.drawString(font, Component.translatable("ae2lt.tianshu.gui.tier", tierName()), 14, 72, 0xE8F4FF, false);
        graphics.drawString(font, Component.translatable("ae2lt.tianshu.gui.cores",
                menu.getCapacityCores(), menu.getParallelCores()), 14, 88, 0xB7C8D8, false);
        graphics.drawString(font, Component.translatable("ae2lt.tianshu.gui.storage", formatStorage(menu.getStorageBytes())),
                14, 104, 0xB7C8D8, false);
        graphics.drawString(font, Component.translatable("ae2lt.tianshu.gui.parallel",
                menu.getParallelism(), menu.isCapped() ? Component.translatable("ae2lt.tianshu.gui.capped") : Component.empty()),
                14, 120, menu.isCapped() ? 0xF2D37A : 0xB7C8D8, false);
    }

    private Component issueText() {
        int ordinal = menu.getIssue();
        var values = TianshuMultiblockScanIssue.values();
        String name = ordinal >= 0 && ordinal < values.length ? values[ordinal].name().toLowerCase(Locale.ROOT) : "unknown";
        return Component.translatable("ae2lt.tianshu.issue." + name);
    }

    private Component tierName() {
        return Component.translatable("ae2lt.tianshu.tier." + menu.getTier().name().toLowerCase(Locale.ROOT));
    }

    private static String formatStorage(long bytes) {
        if (bytes == Long.MAX_VALUE) return "∞";
        if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.2f GiB", bytes / (1024.0 * 1024 * 1024));
        return String.format(Locale.ROOT, "%.0f MiB", bytes / (1024.0 * 1024));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
