package com.moakiee.ae2lt.util;

import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class EnergyText {
    private static final Unit[] UNITS = {
            new Unit(1_000_000_000_000_000_000D, "EFE"),
            new Unit(1_000_000_000_000_000D, "PFE"),
            new Unit(1_000_000_000_000D, "TFE"),
            new Unit(1_000_000_000D, "GFE"),
            new Unit(1_000_000D, "MFE"),
            new Unit(1_000D, "kFE")
    };

    private EnergyText() {
    }

    public static Component storedFe(long stored, long capacity) {
        return label("ae2lt.tooltip.stored_fe")
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatFe(stored)).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatFe(capacity)).withStyle(ChatFormatting.GRAY));
    }

    public static Component capacityFe(long capacity) {
        return label("ae2lt.tooltip.capacity_fe")
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatFe(capacity)).withStyle(ChatFormatting.GRAY));
    }

    public static String formatFe(long value) {
        return formatEnergyValue(Math.max(0L, value));
    }

    private static String formatEnergyValue(long value) {
        for (Unit unit : UNITS) {
            if (value >= unit.threshold) {
                return formatScaled(value / unit.threshold, unit.symbol);
            }
        }
        return Long.toString(value) + " FE";
    }

    private static String formatScaled(double value, String unit) {
        double truncated = Math.floor(value * 100D) / 100D;
        String formatted = String.format(Locale.ROOT, "%.2f", truncated);
        if (formatted.indexOf('.') >= 0) {
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return formatted + " " + unit;
    }

    private static MutableComponent label(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GREEN);
    }

    private record Unit(double threshold, String symbol) {
    }
}
