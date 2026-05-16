package com.moakiee.ae2lt.blockentity.workbench;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.DeviceItem;
import com.moakiee.ae2lt.device.DeviceKind;

public final class DeviceWorkbenchAdapters {
    private static final Map<DeviceKind, DeviceWorkbenchAdapter> BY_KIND = new EnumMap<>(DeviceKind.class);

    static {
        register(ArmorWorkbenchAdapter.INSTANCE);
        register(RailgunWorkbenchAdapter.INSTANCE);
    }

    private DeviceWorkbenchAdapters() {}

    public static void register(DeviceWorkbenchAdapter adapter) {
        BY_KIND.put(adapter.deviceKind(), adapter);
    }

    public static Optional<DeviceWorkbenchAdapter> get(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof DeviceItem device)) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_KIND.get(device.deviceKind()));
    }
}
