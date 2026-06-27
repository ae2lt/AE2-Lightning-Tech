package com.moakiee.ae2lt.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class DeviceHubTooltip {
    private static final String OPEN_DEVICE_HUB_TOOLTIP = "ae2lt.tooltip.open_device_hub";

    private DeviceHubTooltip() {
    }

    public static Component openConfigHint() {
        return Component.translatable(
                OPEN_DEVICE_HUB_TOOLTIP,
                Component.keybind("key.ae2lt.open_config").withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GRAY);
    }
}
