package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.IconButton;

import com.moakiee.ae2lt.AE2LightningTech;

/**
 * Three-state replacement for AE2's two-state blocking button.
 */
final class ProviderBlockingModeButton extends IconButton {
    private static final int STATE_OFF = 0;
    private static final int STATE_NORMAL = 1;
    private static final int STATE_SAME_PATTERN = 2;
    private static final ResourceLocation SAME_PATTERN_TEXTURE =
            new ResourceLocation(AE2LightningTech.MODID, "textures/gui/buttons/same_pattern_blocking_on.png");

    private int state;

    ProviderBlockingModeButton(Button.OnPress onPress) {
        super(onPress);
    }

    void setState(int state) {
        this.state = Math.max(STATE_OFF, Math.min(STATE_SAME_PATTERN, state));
    }

    @Override
    protected Icon getIcon() {
        return switch (state) {
            case STATE_NORMAL -> Icon.BLOCKING_MODE_YES;
            case STATE_SAME_PATTERN -> Icon.TOOLBAR_BUTTON_BACKGROUND;
            default -> Icon.BLOCKING_MODE_NO;
        };
    }

    @Override
    public void renderWidget(
            GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean customIcon = state == STATE_SAME_PATTERN;
        setDisableBackground(customIcon);

        boolean wasActive = active;
        if (customIcon) {
            // The placeholder is the native toolbar background, which should
            // remain opaque while only the custom icon is dimmed.
            active = true;
        }
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        active = wasActive;

        if (customIcon && visible) {
            var blitter = Blitter.texture(SAME_PATTERN_TEXTURE, 16, 16)
                    .src(0, 0, 16, 16);
            if (!wasActive) {
                blitter.opacity(0.5F);
            }
            blitter.dest(getX(), getY())
                    .blit(guiGraphics);
        }
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(Component.translatable(switch (state) {
            case STATE_NORMAL -> "ae2lt.gui.blocking_mode.normal";
            case STATE_SAME_PATTERN -> "ae2lt.gui.blocking_mode.same_pattern";
            default -> "ae2lt.gui.blocking_mode.off";
        }));
    }
}
