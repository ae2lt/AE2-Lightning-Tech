package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

public final class EasterEggOverlay implements LayeredDraw.Layer {
    public static final EasterEggOverlay INSTANCE = new EasterEggOverlay();

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/gui/easter_egg.png");

    private static final int DISPLAY_TICKS = 40;

    private static int ticksRemaining = 0;

    private EasterEggOverlay() {
    }

    public static void trigger() {
        ticksRemaining = DISPLAY_TICKS;
    }

    public static boolean isActive() {
        return ticksRemaining > 0;
    }

    public static void tick() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
        }
    }

    /**
     * Force-clears the overlay state. Called on logout / world unload so the
     * easter-egg image cannot bleed into the next session (and so the static
     * tick counter does not retain references that survive the logical client).
     */
    public static void reset() {
        ticksRemaining = 0;
    }

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (ticksRemaining <= 0) {
            return;
        }

        int elapsed = DISPLAY_TICKS - ticksRemaining;
        float alpha;
        if (elapsed < 2) {
            alpha = 0.0f;
        } else {
            alpha = 1.0f;
        }

        var mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int imgWidth = 512;
        int imgHeight = 436;
        float aspect = (float) imgWidth / imgHeight;

        int maxW = screenWidth * 3 / 4;
        int maxH = screenHeight * 3 / 4;
        int drawW, drawH;
        if ((float) maxW / maxH > aspect) {
            drawH = maxH;
            drawW = (int) (maxH * aspect);
        } else {
            drawW = maxW;
            drawH = (int) (maxW / aspect);
        }
        int x = (screenWidth - drawW) / 2;
        int y = (screenHeight - drawH) / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha);
        guiGraphics.blit(TEXTURE, x, y, drawW, drawH, 0, 0, imgWidth, imgHeight, imgWidth, imgHeight);
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        guiGraphics.pose().popPose();
    }
}
