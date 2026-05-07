package com.moakiee.ae2lt.client.gui;

import java.util.List;

import org.joml.Matrix4f;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import com.moakiee.ae2lt.menu.LargeStackAppEngSlot;
import mezz.jei.api.gui.builder.ITooltipBuilder;

/**
 * Shared rendering for slots whose stack count may exceed 64.
 * <p>
 * Displays abbreviated counts (e.g. {@code 1.5K}, {@code 2.3M}) with a
 * scaled-down font, similar to how AE2 renders amounts in its ME terminal.
 * A tooltip showing the exact count is available via
 * {@link #appendCountTooltip}.
 */
public final class LargeStackCountRenderer {
    private static final float SCALE = 0.75F;
    private static final int SHADOW_COLOR = 0x413f54;
    private static final int TEXT_COLOR = 0xFFFFFF;

    private LargeStackCountRenderer() {
    }

    /**
     * Renders the abbreviated stack count on a slot if it is a
     * {@link LargeStackAppEngSlot} with a count greater than 1.
     */
    public static void renderSlotCount(GuiGraphics guiGraphics, Font font, Slot slot) {
        if (!(slot instanceof LargeStackAppEngSlot)) {
            return;
        }

        var stack = slot.getItem();
        if (stack.isEmpty() || stack.getCount() <= 1) {
            return;
        }

        String text = formatCount(stack.getCount());
        renderLabel(guiGraphics, font, slot.x, slot.y, text);
    }

    /**
     * Renders an abbreviated stack count at a slot position.
     * <p>
     * Intended for contexts like JEI where there is no real {@link Slot}
     * instance, but we still want to reuse the exact same large-stack count
     * visuals as the machine GUI.
     */
    public static void renderCountAt(GuiGraphics guiGraphics, Font font, int slotX, int slotY, long count) {
        if (count <= 1) {
            return;
        }

        renderLabel(guiGraphics, font, slotX, slotY, formatCount(count));
    }

    /**
     * Appends a tooltip line showing the exact count when the hovered slot is a
     * {@link LargeStackAppEngSlot} with a count greater than 1.
     */
    public static void appendCountTooltip(List<Component> lines, Slot slot) {
        if (!(slot instanceof LargeStackAppEngSlot) || !slot.hasItem()) {
            return;
        }

        appendCountTooltip(lines, slot.getItem().getCount());
    }

    /**
     * Appends a tooltip line showing the exact count for any stack-like count
     * source, including JEI recipe ingredients.
     */
    public static void appendCountTooltip(List<Component> lines, long count) {
        if (count > 1) {
            lines.add(Component.translatable("ae2lt.gui.slot_count", String.format("%,d", count))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Appends a tooltip line showing the exact count for JEI rich tooltips.
     */
    public static void appendCountTooltip(ITooltipBuilder tooltip, long count) {
        if (count > 1) {
            tooltip.add(Component.translatable("ae2lt.gui.slot_count", String.format("%,d", count))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Formats a count into an abbreviated form:
     * <ul>
     * <li>{@code < 1000} &rarr; exact number</li>
     * <li>{@code < 1,000,000} &rarr; K suffix (e.g. {@code 1.5K})</li>
     * <li>{@code < 1,000,000,000} &rarr; M suffix (e.g. {@code 2.3M})</li>
     * <li>otherwise &rarr; B suffix</li>
     * </ul>
     */
    public static String formatCount(long count) {
        if (count < 1_000L) {
            return Long.toString(count);
        }
        if (count < 1_000_000L) {
            return formatWithSuffix(count, 1_000L, "K");
        }
        if (count < 1_000_000_000L) {
            return formatWithSuffix(count, 1_000_000L, "M");
        }
        return formatWithSuffix(count, 1_000_000_000L, "B");
    }

    private static String formatWithSuffix(long count, long divisor, String suffix) {
        double value = count / (double) divisor;
        if (value < 10) {
            String formatted = String.format("%.1f", value);
            // "1.0" -> "1"
            if (formatted.endsWith(".0")) {
                formatted = formatted.substring(0, formatted.length() - 2);
            }
            return formatted + suffix;
        }
        return Math.round(value) + suffix;
    }

    private static void renderLabel(GuiGraphics guiGraphics, Font font, int slotX, int slotY, String text) {
        float inverseScale = 1.0F / SCALE;
        int drawX = (int) ((slotX + 18.0F - font.width(text) * SCALE) * inverseScale);
        int drawY = (int) ((slotY + 16.0F - 5.0F * SCALE) * inverseScale);

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 300);
        pose.scale(SCALE, SCALE, SCALE);

        drawShadowedText(pose.last().pose(), font, drawX, drawY, text);

        pose.popPose();
    }

    private static void drawShadowedText(Matrix4f matrix, Font font, int x, int y, String text) {
        // Use the vanilla buffered text path; RenderType.text() manages its own
        // blend / depth state, so we deliberately do NOT toggle RenderSystem
        // blend here -- doing so would pollute the caller's GL state machine
        // (a common source of "exit-game-but-DWM-still-laggy" symptoms when
        // combined with other mods that assume blend remains enabled).
        var buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        font.drawInBatch(text, x + 1, y + 1, SHADOW_COLOR, false, matrix, buffer,
                Font.DisplayMode.NORMAL, 0, 15728880);
        font.drawInBatch(text, x, y, TEXT_COLOR, false, matrix, buffer,
                Font.DisplayMode.NORMAL, 0, 15728880);
        buffer.endBatch();
    }
}
