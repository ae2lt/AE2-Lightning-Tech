package com.moakiee.ae2lt.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;

public final class CelestweaveArmorEnergyLevel implements LayeredDraw.Layer {
    public static final CelestweaveArmorEnergyLevel INSTANCE = new CelestweaveArmorEnergyLevel();

    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 6;
    private static final int INNER_WIDTH = 79;
    private static final int INNER_HEIGHT = 4;
    private static final ResourceLocation BAR_BASE = ResourceLocation.fromNamespaceAndPath(
            "ae2lt", "textures/gui/hud/hud_bar.png");
    private static final ResourceLocation BAR_OVERLAY = ResourceLocation.fromNamespaceAndPath(
            "ae2lt", "textures/gui/hud/hud_bar_overlay.png");

    private CelestweaveArmorEnergyLevel() {
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.gameMode == null
                || !minecraft.gameMode.canHurtPlayer()
                || minecraft.options.hideGui) {
            return;
        }

        long capacity = 0L;
        long stored = 0L;
        for (ItemStack stack : minecraft.player.getArmorSlots()) {
            if (stack.getItem() instanceof BaseCelestweaveArmorItem) {
                capacity = addClamped(capacity, ArmorEnergyBuffer.capacity(stack));
                stored = addClamped(stored, ArmorEnergyBuffer.read(stack));
            }
        }
        if (capacity <= 0L) {
            return;
        }

        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - minecraft.gui.leftHeight + 2;
        int length = Mth.clamp((int) Math.round(((double) Math.min(stored, capacity) / capacity) * INNER_WIDTH),
                0, INNER_WIDTH);

        graphics.blit(BAR_BASE, x, y, 0, 0, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
        if (length > 0) {
            graphics.blit(BAR_OVERLAY, x + 1, y + 1, length, INNER_HEIGHT, 1, 1, length, INNER_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
        }
        minecraft.gui.leftHeight += 8;
    }

    private static long addClamped(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
