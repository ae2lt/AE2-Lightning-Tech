package com.moakiee.ae2lt.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * 统一构造 {@link LightningStatusIconWidget} 悬浮提示的各行文本,确保所有机器展示的
 * "闪电状态" tooltip 在顺序与样式上保持一致。
 */
public final class LightningStatusLines {

    private LightningStatusLines() {
    }

    public static Component title() {
        return Component.translatable("ae2lt.gui.lightning_status.title")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }

    public static Component status(boolean working) {
        return Component.translatable(
                "ae2lt.gui.status.label",
                Component.translatable(working
                        ? "ae2lt.gui.status.working"
                        : "ae2lt.gui.status.idle"));
    }

    public static Component progress(double progress) {
        int percent = (int) Math.round(Math.max(0.0D, Math.min(1.0D, progress)) * 100.0D);
        return Component.translatable("ae2lt.gui.progress.label", percent);
    }

    public static Component energy(long stored, long capacity) {
        return Component.translatable("ae2lt.gui.energy.label", stored, capacity);
    }

    public static Component highVoltage(long amount) {
        return Component.translatable("ae2lt.gui.lightning_status.high_voltage", amount);
    }

    public static Component extremeHighVoltage(long amount) {
        return Component.translatable("ae2lt.gui.lightning_status.extreme_high_voltage", amount);
    }
}
