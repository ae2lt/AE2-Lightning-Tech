package com.moakiee.ae2lt.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Lightweight 1.20.1 compatibility shim for screens ported from newer AE2 UI code.
 */
public class AE2Button extends Button {
    public AE2Button(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }
}
