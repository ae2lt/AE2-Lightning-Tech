package com.moakiee.ae2lt.device.module;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.DeviceItem;
import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.item.railgun.RailgunModuleStorage;

public final class DeviceModuleStorageRegistry {
    private static final Map<DeviceKind, DeviceModuleStorage> BY_KIND = new EnumMap<>(DeviceKind.class);

    static {
        register(RailgunModuleStorage.INSTANCE);
        register(ArmorModuleStorage.HEAD);
        register(ArmorModuleStorage.CHEST);
        register(ArmorModuleStorage.LEGS);
        register(ArmorModuleStorage.FEET);
    }

    private DeviceModuleStorageRegistry() {}

    public static void register(DeviceModuleStorage storage) {
        BY_KIND.put(storage.deviceKind(), storage);
    }

    public static Optional<DeviceModuleStorage> get(DeviceKind kind) {
        return Optional.ofNullable(BY_KIND.get(kind));
    }

    public static Optional<DeviceModuleStorage> get(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof DeviceItem device)) {
            return Optional.empty();
        }
        return get(device.deviceKind());
    }
}
