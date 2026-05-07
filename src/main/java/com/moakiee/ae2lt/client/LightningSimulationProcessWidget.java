package com.moakiee.ae2lt.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import appeng.client.gui.style.Blitter;

import com.moakiee.ae2lt.menu.LightningSimulationChamberMenu;

public class LightningSimulationProcessWidget extends AbstractWidget {
    private static final int STAGE_COUNT = 20;

    private final LightningSimulationChamberMenu menu;
    private final Blitter overlay;

    public LightningSimulationProcessWidget(LightningSimulationChamberMenu menu, Blitter overlay) {
        super(0, 0, overlay.getSrcWidth(), overlay.getSrcHeight(), Component.empty());
        this.menu = menu;
        this.overlay = overlay.copy();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        double progress = menu.getProgress();
        int stage = progress <= 0.0D
                ? 0
                : Mth.clamp((int) Math.ceil(progress * STAGE_COUNT), 1, STAGE_COUNT);
        if (stage <= 0) {
            return;
        }

        int rows = Mth.clamp(Mth.ceil(height * stage / (float) STAGE_COUNT), 0, height);
        int topRows = rows / 2;
        int bottomRows = rows - topRows;

        if (topRows > 0) {
            overlay.copy()
                    .src(overlay.getSrcX(), overlay.getSrcY(), width, topRows)
                    .dest(getX(), getY(), width, topRows)
                    .blit(guiGraphics);
        }

        if (bottomRows > 0) {
            int srcY = overlay.getSrcY() + height - bottomRows;
            int destY = getY() + height - bottomRows;
            overlay.copy()
                    .src(overlay.getSrcX(), srcY, width, bottomRows)
                    .dest(getX(), destY, width, bottomRows)
                    .blit(guiGraphics);
        }
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
