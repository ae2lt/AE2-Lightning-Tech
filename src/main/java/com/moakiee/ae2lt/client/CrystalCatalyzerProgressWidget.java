package com.moakiee.ae2lt.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import appeng.client.gui.style.Blitter;

import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;

public class CrystalCatalyzerProgressWidget extends AbstractWidget {
    private final CrystalCatalyzerMenu menu;
    private final Blitter overlay;

    public CrystalCatalyzerProgressWidget(CrystalCatalyzerMenu menu, Blitter overlay) {
        super(0, 0, overlay.getSrcWidth(), overlay.getSrcHeight(), Component.empty());
        this.menu = menu;
        this.overlay = overlay.copy();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        double progress = menu.getProgress();
        if (progress <= 0.0D) {
            return;
        }

        int filled = Mth.clamp((int) Math.round(width * progress), 0, width);
        if (filled <= 0) {
            return;
        }

        overlay.copy()
                .src(overlay.getSrcX(), overlay.getSrcY(), filled, height)
                .dest(getX(), getY(), filled, height)
                .blit(guiGraphics);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }
}
