package com.moakiee.ae2lt.integration.emi;

import appeng.api.client.AEKeyRendering;
import com.moakiee.ae2lt.me.key.LightningKey;
import dev.emi.emi.api.render.EmiRenderable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Animated lightning icon shared by EMI category headers and recipe pages. */
final class EmiLightningIcon implements EmiRenderable {
    private final boolean extreme;

    EmiLightningIcon(boolean extreme) {
        this.extreme = extreme;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float delta) {
        AEKeyRendering.drawInGui(
                Minecraft.getInstance(),
                graphics,
                x,
                y,
                extreme ? LightningKey.EXTREME_HIGH_VOLTAGE : LightningKey.HIGH_VOLTAGE);
    }
}
