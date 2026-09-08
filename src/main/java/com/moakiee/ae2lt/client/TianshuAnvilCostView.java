package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Embeds the native anvil cost renderer, including modifications to AnvilScreen. */
final class TianshuAnvilCostView extends AnvilScreen {
    private final TianshuCraftingTermMenu terminalMenu;

    TianshuAnvilCostView(TianshuCraftingTermMenu terminalMenu, Inventory inventory) {
        super(terminalMenu.getAnvil(), inventory, Component.empty());
        this.terminalMenu = terminalMenu;
        // The terminal owns labels, widgets, slot clicks and rename packets. This view is never
        // opened or initialized as a screen, so it adds no listeners or native rename widget.
        titleLabelY = inventoryLabelY = -10000;
    }

    void renderCost(GuiGraphics graphics, int x, int y, int availableWidth, int mouseX, int mouseY) {
        minecraft = Minecraft.getInstance();
        font = minecraft.font;
        // Slot synchronization can recalculate the client-side engine after the cost arrives.
        // Restore the authoritative cost before the native renderer consults this same engine.
        menu.setMaximumCost(terminalMenu.anvilCost);
        // Measure both standard labels only to fit the narrow WT pane; the parent still chooses what to display.
        int labelWidth = Math.max(font.width(Component.translatable("container.repair.cost", terminalMenu.anvilCost)),
                font.width(Component.translatable("container.repair.expensive")));
        float scale = Math.min(1.0f, (availableWidth - 4.0f) / Math.max(1, labelWidth));
        imageWidth = (int) Math.floor(availableWidth / scale) + 8;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(0, -69, 0);
        try {
            super.renderLabels(graphics, mouseX, mouseY);
        } finally {
            graphics.pose().popPose();
        }
    }
}
