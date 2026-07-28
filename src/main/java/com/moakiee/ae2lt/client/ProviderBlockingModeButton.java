package com.moakiee.ae2lt.client;

import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;

/**
 * Three-state replacement for AE2's two-state blocking button.
 */
final class ProviderBlockingModeButton extends IconButton {
    private static final int STATE_OFF = 0;
    private static final int STATE_NORMAL = 1;
    private static final int STATE_SAME_PATTERN = 2;

    private int state;

    ProviderBlockingModeButton(Button.OnPress onPress) {
        super(onPress);
    }

    void setState(int state) {
        this.state = Math.clamp(state, STATE_OFF, STATE_SAME_PATTERN);
    }

    @Override
    protected Icon getIcon() {
        return switch (state) {
            case STATE_NORMAL -> Icon.BLOCKING_MODE_YES;
            case STATE_SAME_PATTERN -> Icon.SCHEDULING_ROUND_ROBIN;
            default -> Icon.BLOCKING_MODE_NO;
        };
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
