package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ITooltip;

import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;

public class CrystalCatalyzerEnergyBar extends AbstractWidget implements ITooltip {
    private final CrystalCatalyzerMenu menu;
    private final Blitter fill;

    public CrystalCatalyzerEnergyBar(CrystalCatalyzerMenu menu, Blitter fill) {
        super(0, 0, fill.getSrcWidth(), fill.getSrcHeight(), Component.empty());
        this.menu = menu;
        this.fill = fill.copy();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        fill.copy().opacity(0.2f).dest(getX(), getY(), width, height).blit(guiGraphics);

        long capacity = Math.max(1L, menu.getEnergyCapacity());
        long stored = Math.min(Math.max(0L, menu.getStoredEnergy()), capacity);
        int filled = (int) Math.round(height * (double) stored / (double) capacity);
        if (filled <= 0) {
            return;
        }
        filled = Math.min(filled, height);

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
                "ae2lt.gui.crystal_catalyzer.energy.tooltip",
                menu.getStoredEnergy(),
                menu.getEnergyCapacity()));
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
