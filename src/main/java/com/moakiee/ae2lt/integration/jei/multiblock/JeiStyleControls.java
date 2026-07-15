package com.moakiee.ae2lt.integration.jei.multiblock;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Code-drawn controls that follow JEI's beveled button palette and state treatment. */
final class JeiStyleControls {
    private static final int BORDER_COLOR = 0xFF000000;
    private static final int HOVER_BORDER_COLOR = 0xFFFFFFFF;
    private static final int FACE_COLOR = 0xFF6E6E6E;
    private static final int PRESSED_FACE_COLOR = 0xFF6D6D6D;
    private static final int DISABLED_FACE_COLOR = 0xFF2B2B2B;
    private static final int LIGHT_EDGE_COLOR = 0xFFAAAAAA;
    private static final int DARK_EDGE_COLOR = 0xFF555555;
    private static final int PRESSED_LIGHT_EDGE_COLOR = 0xFF707070;
    private static final int DISABLED_LIGHT_EDGE_COLOR = 0xFF303030;
    private static final int DISABLED_DARK_EDGE_COLOR = 0xFF242424;
    private static final int ICON_COLOR = 0xFFE0E0E0;
    private static final int HOVER_ICON_COLOR = 0xFFFFFFFF;
    private static final int DISABLED_ICON_COLOR = 0xFFA0A0A0;
    private static final int INSET_FACE_COLOR = 0xFF303030;

    static void drawIconButton(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int height,
            PixelIcon icon,
            boolean pressed,
            boolean contentPressed,
            boolean enabled,
            boolean hovered) {
        drawButtonFrame(guiGraphics, x, y, width, height, pressed, enabled, enabled && hovered);
        int color = !enabled
                ? DISABLED_ICON_COLOR
                : hovered ? HOVER_ICON_COLOR : ICON_COLOR;
        drawPixelIcon(guiGraphics, x, y, width, height, icon, color, contentPressed);
    }

    static void drawTextButton(
            GuiGraphics guiGraphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            Component label,
            boolean pressed,
            boolean hovered) {
        drawButtonFrame(guiGraphics, x, y, width, height, pressed, true, hovered);
        String value = label.getString();
        int maxWidth = Math.max(0, width - 6);
        if (font.width(value) > maxWidth) {
            value = font.plainSubstrByWidth(value, maxWidth);
        }
        int textX = x + (width - font.width(value)) / 2;
        int textY = y + (height - font.lineHeight) / 2 + 1;
        int color = hovered ? HOVER_ICON_COLOR : ICON_COLOR;
        guiGraphics.drawString(font, value, textX, textY, color, false);
    }

    static void drawInsetLabel(
            GuiGraphics guiGraphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            Component label) {
        int right = x + width;
        int bottom = y + height;
        guiGraphics.fill(x, y, right, bottom, BORDER_COLOR);
        guiGraphics.fill(x + 1, y + 1, right - 1, bottom - 1, INSET_FACE_COLOR);
        guiGraphics.fill(x + 1, y + 1, right - 1, y + 2, DARK_EDGE_COLOR);
        guiGraphics.fill(x + 1, y + 1, x + 2, bottom - 1, DARK_EDGE_COLOR);
        guiGraphics.fill(x + 1, bottom - 2, right - 1, bottom - 1, LIGHT_EDGE_COLOR);
        guiGraphics.fill(right - 2, y + 1, right - 1, bottom - 1, LIGHT_EDGE_COLOR);

        String value = label.getString();
        int maxWidth = Math.max(0, width - 6);
        if (font.width(value) > maxWidth) {
            value = font.plainSubstrByWidth(value, maxWidth);
        }
        int textX = x + (width - font.width(value)) / 2;
        int textY = y + (height - font.lineHeight) / 2 + 1;
        guiGraphics.drawString(font, value, textX, textY, ICON_COLOR, false);
    }

    private static void drawButtonFrame(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int height,
            boolean pressed,
            boolean enabled,
            boolean hovered) {
        int right = x + width;
        int bottom = y + height;
        int border = hovered ? HOVER_BORDER_COLOR : BORDER_COLOR;
        int face = !enabled ? DISABLED_FACE_COLOR : pressed ? PRESSED_FACE_COLOR : FACE_COLOR;
        int topLeft = !enabled
                ? DISABLED_LIGHT_EDGE_COLOR
                : pressed ? DARK_EDGE_COLOR : LIGHT_EDGE_COLOR;
        int bottomRight = !enabled
                ? DISABLED_DARK_EDGE_COLOR
                : pressed ? PRESSED_LIGHT_EDGE_COLOR : DARK_EDGE_COLOR;

        guiGraphics.fill(x, y, right, bottom, border);
        guiGraphics.fill(x + 1, y + 1, right - 1, bottom - 1, face);
        guiGraphics.fill(x + 1, y + 1, right - 1, y + 2, topLeft);
        guiGraphics.fill(x + 1, y + 1, x + 2, bottom - 1, topLeft);
        guiGraphics.fill(x + 1, bottom - 2, right - 1, bottom - 1, bottomRight);
        guiGraphics.fill(right - 2, y + 1, right - 1, bottom - 1, bottomRight);
    }

    private static void drawPixelIcon(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int height,
            PixelIcon icon,
            int color,
            boolean contentPressed) {
        String[] pixels = icon.pixels;
        double pressOffset = contentPressed ? 0.5D : 0.0D;
        double startX = x + (width - icon.inkWidth) / 2.0D - icon.minX + pressOffset;
        double startY = y + (height - icon.inkHeight) / 2.0D - icon.minY + pressOffset;
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(startX, startY, 0.0D);

        for (int row = 0; row < pixels.length; row++) {
            String line = pixels[row];
            int column = 0;
            while (column < line.length()) {
                int runStart = line.indexOf('#', column);
                if (runStart < 0) {
                    break;
                }
                int runEnd = runStart + 1;
                while (runEnd < line.length() && line.charAt(runEnd) == '#') {
                    runEnd++;
                }
                guiGraphics.fill(
                        runStart,
                        row,
                        runEnd,
                        row + 1,
                        color);
                column = runEnd;
            }
        }
        pose.popPose();
    }

    enum PixelIcon {
        RESET_VIEW(
                "...###...",
                "...#.#...",
                "...#.#...",
                "###...###",
                "#...#...#",
                "###...###",
                "...#.#...",
                "...#.#...",
                "...###..."),
        PLAY(
                "..#......",
                "..##.....",
                "..###....",
                "..####...",
                "..#####..",
                "..####...",
                "..###....",
                "..##.....",
                "..#......"),
        PAUSE(
                ".........",
                "..##.##..",
                "..##.##..",
                "..##.##..",
                "..##.##..",
                "..##.##..",
                "..##.##..",
                "..##.##..",
                "........."),
        SHELL(
                "...###...",
                ".##...##.",
                "#.#....#.",
                "#..#...#.",
                "#...#..#.",
                "#....#.#.",
                ".##...##.",
                "...###...",
                "........."),
        SHELL_HIDDEN(
                "...###..#",
                ".##...###",
                "#.#...##.",
                "#..#.##..",
                "#...##.#.",
                "#..##..#.",
                ".###..##.",
                ".####....",
                "#........"),
        LAYERS_FULL(
                ".#######.",
                "..#####..",
                ".........",
                ".#######.",
                "..#####..",
                ".........",
                ".#######.",
                "..#####..",
                "........."),
        LAYERS_UP_TO(
                ".........",
                ".........",
                ".........",
                ".#######.",
                "..#####..",
                ".........",
                ".#######.",
                "..#####..",
                "........."),
        LAYERS_SINGLE(
                ".........",
                ".........",
                ".........",
                ".#######.",
                "..#####..",
                ".........",
                ".........",
                ".........",
                "........."),
        MINUS(
                ".........",
                ".........",
                ".........",
                ".........",
                "..#####..",
                ".........",
                ".........",
                ".........",
                "........."),
        PLUS(
                ".........",
                "....#....",
                "....#....",
                "....#....",
                "..#####..",
                "....#....",
                "....#....",
                "....#....",
                ".........");

        private final String[] pixels;
        private final int minX;
        private final int minY;
        private final int inkWidth;
        private final int inkHeight;

        PixelIcon(String... pixels) {
            this.pixels = pixels;
            int left = Integer.MAX_VALUE;
            int top = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            int bottom = Integer.MIN_VALUE;
            for (int y = 0; y < pixels.length; y++) {
                String row = pixels[y];
                for (int x = 0; x < row.length(); x++) {
                    if (row.charAt(x) == '#') {
                        left = Math.min(left, x);
                        top = Math.min(top, y);
                        right = Math.max(right, x);
                        bottom = Math.max(bottom, y);
                    }
                }
            }
            if (left == Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Pixel icon must not be empty");
            }
            this.minX = left;
            this.minY = top;
            this.inkWidth = right - left + 1;
            this.inkHeight = bottom - top + 1;
        }
    }

    private JeiStyleControls() {
    }
}
