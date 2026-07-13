package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.config.AE2LTClientConfig;
import net.minecraft.client.gui.screens.Screen;

public final class TianshuUploadTriggerClient {
    private TianshuUploadTriggerClient() {
    }

    public static boolean shouldTrigger() {
        return switch (AE2LTClientConfig.uploadTrigger()) {
            case NO_SHIFT -> !Screen.hasShiftDown();
            case SHIFT -> Screen.hasShiftDown();
            case CTRL -> Screen.hasControlDown();
            case ALT -> Screen.hasAltDown();
            case MANUAL_ONLY -> false;
        };
    }
}
