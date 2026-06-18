package com.moakiee.ae2lt.device.module;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;

/**
 * Static spec for a single module slot on a device.
 *
 * @param slot       semantic slot type
 * @param index      logical index within slots of the same type (0 for unique slots)
 * @param capacity   max stack size accepted (usually 1)
 * @param accepts    which device kind this slot belongs to (always single-device)
 */
public record ModuleSlotSpec(DeviceSlotType slot, int index, int capacity, DeviceKind accepts) {

    public static ModuleSlotSpec of(DeviceSlotType slot, DeviceKind accepts) {
        return new ModuleSlotSpec(slot, 0, 1, accepts);
    }

    public static ModuleSlotSpec of(DeviceSlotType slot, int index, DeviceKind accepts) {
        return new ModuleSlotSpec(slot, index, 1, accepts);
    }
}
