package com.moakiee.ae2lt.device.module;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.capability.DeviceCapability;

/**
 * Module item interface for overload devices. Most modules use one slot type for every supported
 * device; cross-device modules can override {@link #accepts(DeviceKind, DeviceSlotType)} when the
 * corresponding slot differs between devices.
 *
 * <p>Devices consume only the capability list — they never branch on the concrete
 * module item class. Lifecycle is opt-in via {@link #asSubmodule()}; pure data
 * modules (e.g. railgun modules) return {@code null}.
 */
public interface OverloadDeviceModuleItem {

    Set<DeviceKind> acceptableDevices();

    DeviceSlotType acceptableSlot();

    default boolean accepts(DeviceKind deviceKind, DeviceSlotType slotType) {
        return acceptableDevices().contains(deviceKind) && acceptableSlot() == slotType;
    }

    /** Stable storage key used when a device persists and merges installed module stacks. */
    default String moduleTypeId(ItemStack stack) {
        return "";
    }

    /** Per-device install cap. Zero means limited only by available device slots. */
    default int getMaxInstallAmount() {
        return 0;
    }

    List<DeviceCapability> capabilities(ItemStack stack);

    @Nullable
    default OverloadDeviceSubmodule asSubmodule() {
        return null;
    }
}
