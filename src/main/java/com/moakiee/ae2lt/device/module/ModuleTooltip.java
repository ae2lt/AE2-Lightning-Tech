package com.moakiee.ae2lt.device.module;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.moakiee.ae2lt.device.DeviceKind;

public final class ModuleTooltip {
    private ModuleTooltip() {
    }

    public static void appendInstallInfo(OverloadDeviceModuleItem module, List<Component> tooltip) {
        if (module == null) {
            return;
        }
        tooltip.add(Component.translatable("ae2lt.module.tooltip.installable_on")
                .withStyle(ChatFormatting.GRAY));
        module.acceptableDevices().stream()
                .sorted(java.util.Comparator.comparing(DeviceKind::name))
                .forEach(kind -> tooltip.add(deviceLine(kind).withStyle(ChatFormatting.GRAY)));
    }

    private static MutableComponent deviceLine(DeviceKind kind) {
        return Component.literal(" - ").append(Component.translatable(deviceKey(kind)));
    }

    private static String deviceKey(DeviceKind kind) {
        return "ae2lt.module.device." + kind.name().toLowerCase(java.util.Locale.ROOT);
    }

}
