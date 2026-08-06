package com.moakiee.ae2lt.client.gui;

public final class GuiTextLayout {
    private GuiTextLayout() {
    }

    public static int rightAlignedX(int areaWidth, int rightPadding, int textWidth, int minX) {
        return Math.max(minX, areaWidth - rightPadding - textWidth);
    }

    public static int centeredX(int areaWidth, int textWidth) {
        return (areaWidth - textWidth) / 2;
    }
}
