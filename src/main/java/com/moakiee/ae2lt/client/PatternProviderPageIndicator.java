package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.client.gui.GuiTextLayout;

public final class PatternProviderPageIndicator {
    private PatternProviderPageIndicator() {
    }

    public static int centeredX(int guiWidth, int textWidth) {
        return GuiTextLayout.centeredX(guiWidth, textWidth);
    }
}
