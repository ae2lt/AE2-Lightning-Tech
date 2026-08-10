package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.network.chat.Component;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;

import com.moakiee.ae2lt.me.cell.VoidCellMode;

final class VoidCellModeButton extends IconButton {
    private final VoidCellMode mode;

    VoidCellModeButton(VoidCellMode mode, OnPress onPress) {
        super(onPress);
        this.mode = mode;
    }

    @Override
    protected Icon getIcon() {
        return switch (mode) {
            case TRASH -> Icon.CONDENSER_OUTPUT_TRASH;
            case MATTER_BALLS -> Icon.CONDENSER_OUTPUT_MATTER_BALL;
            case SINGULARITY -> Icon.CONDENSER_OUTPUT_SINGULARITY;
        };
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(Component.translatable("gui.ae2lt.void_cell.mode." + mode.ordinal()));
    }
}
