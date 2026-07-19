package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Shared pixel-atlas layout for the AdvancedAE and overload pattern popups. */
final class TianshuPatternConfigLayout {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/tianshu_pattern_config.png");
    static final int TEXTURE_SIZE = 256;
    static final int GUI_WIDTH = 190;
    static final int HEADER_HEIGHT = 32;
    static final int ROW_HEIGHT = 25;
    static final int VISIBLE_ROWS = 5;
    static final int FOOTER_HEIGHT = 30;
    static final int GUI_HEIGHT = HEADER_HEIGHT + VISIBLE_ROWS * ROW_HEIGHT + FOOTER_HEIGHT;
    static final int ROW_LEFT = 8;
    static final int ROW_TEXTURE_X = 9;
    static final int ROW_TEXTURE_Y = 190;
    static final int ROW_TEXTURE_WIDTH = 158;
    static final int ROW_CONTENT_X_OFFSET = 4;
    static final int SCROLLBAR_HEIGHT = 129;

    private TianshuPatternConfigLayout() {
    }

    static void drawBackground(GuiGraphics graphics, int offsetX, int offsetY) {
        graphics.blit(TEXTURE, offsetX, offsetY,
                0, 0, GUI_WIDTH, GUI_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        for (int visible = 0; visible < VISIBLE_ROWS; visible++) {
            int top = offsetY + HEADER_HEIGHT + visible * ROW_HEIGHT;
            graphics.blit(TEXTURE, offsetX + ROW_TEXTURE_X, top,
                    ROW_TEXTURE_X, ROW_TEXTURE_Y, ROW_TEXTURE_WIDTH, ROW_HEIGHT,
                    TEXTURE_SIZE, TEXTURE_SIZE);
        }
    }
}
