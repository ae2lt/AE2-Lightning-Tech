package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.gui.widgets.ITooltip;

final class OutputSidesClearButton extends Button implements ITooltip {
    OutputSidesClearButton(Component tooltip, OnPress onPress) {
        super(0, 0, 8, 8, tooltip, onPress, Button.DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (visible) {
            OutputSideButtonStyle.renderClearIcon(graphics, getX(), getY());
        }
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(getMessage());
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), getWidth(), getHeight());
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return visible;
    }
}
