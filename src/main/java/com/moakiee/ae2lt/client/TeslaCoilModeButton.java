package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.IconButton;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilMode;

public class TeslaCoilModeButton extends IconButton {
    private static final ResourceLocation HV_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/buttons/lightning.png");
    private static final ResourceLocation EHV_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/buttons/lightning_high_voltage.png");

    private TeslaCoilMode mode = TeslaCoilMode.HIGH_VOLTAGE;

    public TeslaCoilModeButton(OnPress onPress) {
        super(onPress);
    }

    public void setMode(TeslaCoilMode mode) {
        this.mode = mode;
    }

    @Override
    protected Icon getIcon() {
        return null;
    }

    @Override
    protected Item getItemOverlay() {
        return null;
    }

    @Override
    public List<Component> getTooltipMessage() {
        var current = Component.translatable("ae2lt.gui.tesla_coil.mode."
                + (mode == TeslaCoilMode.EXTREME_HIGH_VOLTAGE ? "extreme_high_voltage" : "high_voltage"));
        var next = Component.translatable("ae2lt.gui.tesla_coil.mode."
                + (mode == TeslaCoilMode.EXTREME_HIGH_VOLTAGE ? "high_voltage" : "extreme_high_voltage"));
        return List.of(
                Component.translatable("ae2lt.gui.tesla_coil.mode.button", current),
                Component.translatable("ae2lt.gui.tesla_coil.mode.click_to_switch", next));
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
        if (!this.visible) {
            return;
        }

        int yOffset = isHovered() ? 1 : 0;
        Icon bgIcon = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : mode == TeslaCoilMode.EXTREME_HIGH_VOLTAGE
                        ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS
                        : Icon.TOOLBAR_BUTTON_BACKGROUND;
        bgIcon.getBlitter()
                .dest(getX() - 1, getY() + yOffset, 18, 20)
                .zOffset(2)
                .blit(guiGraphics);

        var texture = mode == TeslaCoilMode.EXTREME_HIGH_VOLTAGE ? EHV_TEXTURE : HV_TEXTURE;
        Blitter.texture(texture, 16, 16)
                .src(0, 0, 16, 16)
                .dest(getX(), getY() + 1 + yOffset, 16, 16)
                .zOffset(3)
                .blit(guiGraphics);
    }
}
