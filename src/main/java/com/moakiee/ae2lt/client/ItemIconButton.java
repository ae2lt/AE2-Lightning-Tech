package com.moakiee.ae2lt.client;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;

/** AE2-style toolbar button that renders an item stack as its icon. */
public class ItemIconButton extends IconButton {

    private final Item item;

    public ItemIconButton(Item item, Component tooltip, OnPress onPress) {
        super(onPress);
        this.item = item;
        setDisableBackground(true);
        setMessage(tooltip);
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    protected Icon getIcon() {
        return Icon.TOOLBAR_BUTTON_BACKGROUND;
    }

    @Override
    protected Item getItemOverlay() {
        return item;
    }
}
