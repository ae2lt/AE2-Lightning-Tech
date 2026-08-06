package com.moakiee.ae2lt.client;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;

public class OverloadProcessingFactoryOutputButton extends IconButton {
    private final Component sideLabel;
    private ItemStack display = ItemStack.EMPTY;
    private boolean on;

    public OverloadProcessingFactoryOutputButton(Component sideLabel, OnPress onPress) {
        super(onPress);
        this.sideLabel = sideLabel;
    }

    public void setDisplay(@Nullable ItemLike itemLike) {
        this.display = itemLike == null ? ItemStack.EMPTY : new ItemStack(itemLike);
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    @Override
    protected Icon getIcon() {
        return null;
    }

    @Override
    protected Item getItemOverlay() {
        return display.isEmpty() ? null : display.getItem();
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                sideLabel,
                Component.translatable(on
                        ? "ae2lt.gui.overload_factory.output_side.enabled"
                        : "ae2lt.gui.overload_factory.output_side.disabled"));
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
        if (!this.visible) {
            return;
        }

        var yOffset = isHovered() ? 1 : 0;
        Icon bgIcon = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : on ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;
        bgIcon.getBlitter()
                .dest(getX() - 1, getY() + yOffset, 18, 20)
                .zOffset(2)
                .blit(guiGraphics);

        if (!display.isEmpty()) {
            guiGraphics.renderItem(display, getX(), getY() + 1 + yOffset, 0, 3);
        }
    }
}
