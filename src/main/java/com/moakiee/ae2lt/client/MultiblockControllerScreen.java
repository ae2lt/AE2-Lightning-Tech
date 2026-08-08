package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Shared chrome for multiblock controller screens: status strip, data rows, gauge. */
public abstract class MultiblockControllerScreen<T extends AbstractContainerMenu>
        extends AbstractContainerScreen<T> {

    protected static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/guis/multiblock_controller.png");

    // Texture regions: title 2-14, status recess 18-34, divider 36-46, body recess 50-132, footer 134-162
    protected static final int TEXT_X = 12;
    protected static final int VALUE_RIGHT = 197;
    protected static final int TITLE_Y = 5;
    protected static final int STATUS_TEXT_Y = 22;
    protected static final int ROW_Y = 56;
    protected static final int LINE_H = 13;
    protected static final int HINT_GAP = 17;
    protected static final int FOOTER_ROW_Y = 138;
    protected static final int FOOTER_MID_Y = 144;

    protected static final int COL_TITLE = 0x2A2E3F;
    protected static final int COL_LABEL = 0x4E5370;
    protected static final int COL_VALUE = 0x14161F;
    protected static final int COL_MUTED = 0x565C78;
    protected static final int COL_GREEN = 0x1C6B34;
    protected static final int COL_AMBER = 0x7A5E14;
    protected static final int COL_RED = 0x8F2B23;
    protected static final int COL_BLUE = 0x1F4E79;

    protected MultiblockControllerScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 209;
        imageHeight = 167;
        inventoryLabelY = 10_000;
        titleLabelY = 10_000;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    protected void drawTitle(GuiGraphics guiGraphics) {
        guiGraphics.drawString(font, title, TEXT_X - 4, TITLE_Y, COL_TITLE, false);
    }

    /** Status strip line with an indicator dot, inside the upper recess. */
    protected void drawStatus(GuiGraphics guiGraphics, Component text, int color) {
        guiGraphics.fill(TEXT_X, 24, TEXT_X + 4, 28, 0xFF000000 | color);
        guiGraphics.drawString(font, text, TEXT_X + 8, STATUS_TEXT_Y, color, false);
    }

    /** Data row: label on the left, value right-aligned. */
    protected void drawRow(GuiGraphics guiGraphics, int y, Component label, String value, int valueColor) {
        guiGraphics.drawString(font, label, TEXT_X, y, COL_LABEL, false);
        guiGraphics.drawString(font, value, VALUE_RIGHT - font.width(value), y, valueColor, false);
    }

    /** Word-wrapped issue text at the top of the body recess, with a muted hint below. */
    protected void drawUnformed(GuiGraphics guiGraphics, Component issue, String hintKey) {
        int y = ROW_Y;
        for (var line : font.split(issue, VALUE_RIGHT - TEXT_X)) {
            guiGraphics.drawString(font, line, TEXT_X, y, COL_RED, false);
            y += 11;
        }
        guiGraphics.drawString(font, Component.translatable(hintKey),
                TEXT_X, y - 11 + HINT_GAP, COL_MUTED, false);
    }

    /** Footer gauge: recessed frame, quarter ticks, beveled fill; markLo/markHi < 0 hides the target zone. */
    protected void drawGauge(GuiGraphics guiGraphics, double fill, int fillColor,
            double markLo, double markHi) {
        int x0 = TEXT_X, y0 = 149, w = VALUE_RIGHT - TEXT_X, h = 10;

        // frame in the texture's recess style: dark top/left, white bottom/right
        guiGraphics.fill(x0 - 1, y0 - 1, x0 + w + 1, y0, 0xFF696D88);
        guiGraphics.fill(x0 - 1, y0 - 1, x0, y0 + h + 1, 0xFF696D88);
        guiGraphics.fill(x0 - 1, y0 + h, x0 + w + 1, y0 + h + 1, 0xFFF2F2F2);
        guiGraphics.fill(x0 + w, y0, x0 + w + 1, y0 + h + 1, 0xFFF2F2F2);

        // well with quarter ticks, matching the texture's recess tone
        guiGraphics.fill(x0, y0, x0 + w, y0 + h, 0xFF9A9FB4);
        for (int i = 1; i < 4; i++) {
            int tx = x0 + w * i / 4;
            guiGraphics.fill(tx, y0, tx + 1, y0 + h, 0xFF868BA4);
        }

        // target-zone bed, visible until covered by the fill
        int lo = x0 + (int) Math.round(w * markLo);
        int hi = x0 + (int) Math.round(w * markHi);
        if (markLo >= 0.0D) {
            guiGraphics.fill(lo, y0, hi, y0 + h, 0xFF79A186);
        }

        // beveled fill: highlight / body / shade
        int fw = Math.max(0, Math.min(w, (int) Math.round(w * fill)));
        if (fw > 0) {
            guiGraphics.fill(x0, y0, x0 + fw, y0 + 1, mix(fillColor, 0xFFFFFF, 0.45D));
            guiGraphics.fill(x0, y0 + 1, x0 + fw, y0 + h - 1, fillColor);
            guiGraphics.fill(x0, y0 + h - 1, x0 + fw, y0 + h, mix(fillColor, 0x000000, 0.30D));
        }

        // target-zone boundary pins, always on top
        if (markLo >= 0.0D) {
            guiGraphics.fill(lo, y0, lo + 1, y0 + h, 0xFFF2F2F2);
            guiGraphics.fill(hi - 1, y0, hi, y0 + h, 0xFFF2F2F2);
        }
    }

    private static int mix(int color, int target, double k) {
        int r = (int) Math.round(((color >> 16) & 0xFF) * (1.0D - k) + ((target >> 16) & 0xFF) * k);
        int g = (int) Math.round(((color >> 8) & 0xFF) * (1.0D - k) + ((target >> 8) & 0xFF) * k);
        int b = (int) Math.round((color & 0xFF) * (1.0D - k) + (target & 0xFF) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    protected static String formatCount(long value) {
        return value == Long.MAX_VALUE || value == Integer.MAX_VALUE
                ? "∞" : String.format(Locale.ROOT, "%,d", value);
    }

    protected static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0D);
    }
}
