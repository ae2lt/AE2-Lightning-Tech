package com.moakiee.ae2lt.device.module;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.capability.DeviceCapability;

/**
 * Module item interface for overload devices. Each module is tied to exactly one
 * device kind (single-element {@link #acceptableDevices()}) and one slot type.
 *
 * <p>Devices consume only the capability list — they never branch on the concrete
 * module item class. Lifecycle is opt-in via {@link #asSubmodule()}; pure data
 * modules (e.g. railgun modules) return {@code null}.
 */
public interface OverloadDeviceModuleItem {

    Set<DeviceKind> acceptableDevices();

    DeviceSlotType acceptableSlot();

    List<DeviceCapability> capabilities(ItemStack stack);

    @Nullable
    default OverloadDeviceSubmodule asSubmodule() {
        return null;
    }
}
