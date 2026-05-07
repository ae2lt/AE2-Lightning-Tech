package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ITooltip;

import com.moakiee.ae2lt.menu.AtmosphericIonizerMenu;

public class AtmosphericIonizerEnergyBar extends AbstractWidget implements ITooltip {
    private final AtmosphericIonizerMenu menu;
    private final Blitter fill;

    public AtmosphericIonizerEnergyBar(AtmosphericIonizerMenu menu, Blitter fill) {
        super(0, 0, fill.getSrcWidth(), fill.getSrcHeight(), Component.empty());
        this.menu = menu;
        this.fill = fill.copy();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        fill.copy().opacity(0.2f).dest(getX(), getY(), width, height).blit(guiGraphics);

        long totalEnergy = Math.max(1L, menu.getTotalEnergyRequired());
        long consumedEnergy = Math.min(menu.getConsumedEnergy(), totalEnergy);
        int filled = (int) Math.round(height * (double) consumedEnergy / (double) totalEnergy);
        if (filled <= 0) {
            return;
        }

        int srcY = fill.getSrcY() + height - filled;
        int destY = getY() + height - filled;
        fill.copy()
                .src(fill.getSrcX(), srcY, width, filled)
                .dest(getX(), destY, width, filled)
                .blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(Component.translatable(
                "ae2lt.gui.atmospheric_ionizer.energy.tooltip",
                menu.getConsumedEnergy(),
                menu.getTotalEnergyRequired()));
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX() - 2, getY() - 2, width + 4, height + 4);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }
}
