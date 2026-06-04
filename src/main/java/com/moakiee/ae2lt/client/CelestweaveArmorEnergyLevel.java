package com.moakiee.ae2lt.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.overload.armor.ArmorEnergyBuffer;
import com.moakiee.ae2lt.overload.armor.BaseOverloadArmorItem;

public final class CelestweaveArmorEnergyLevel implements LayeredDraw.Layer {
    public static final CelestweaveArmorEnergyLevel INSTANCE = new CelestweaveArmorEnergyLevel();

    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 6;
    private static final int INNER_WIDTH = 79;
    private static final int INNER_HEIGHT = 4;
    private static final int FRAME_COLOR = 0xAA000000;
    private static final int EMPTY_COLOR = 0xCC1B2430;
    private static final int ENERGY_COLOR = 0xFF38D7FF;

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
            if (stack.getItem() instanceof BaseOverloadArmorItem) {
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

        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, FRAME_COLOR);
        graphics.fill(x + 1, y + 1, x + 1 + INNER_WIDTH, y + 1 + INNER_HEIGHT, EMPTY_COLOR);
        if (length > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + length, y + 1 + INNER_HEIGHT, ENERGY_COLOR);
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
