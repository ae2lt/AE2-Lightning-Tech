package com.moakiee.ae2lt.item.railgun;

import java.util.Set;

import com.moakiee.ae2lt.device.DeviceKind;

public final class RailgunEnergyModuleRules {
    private static final Set<DeviceKind> ACCEPTED_DEVICE_KINDS = Set.of(
            DeviceKind.CELESTWEAVE_OCULUS,
            DeviceKind.CELESTWEAVE_CORE,
            DeviceKind.CELESTWEAVE_CONDUIT,
            DeviceKind.CELESTWEAVE_STRIDE,
            DeviceKind.RAILGUN);

    private RailgunEnergyModuleRules() {
    }

    public static Set<DeviceKind> acceptedDeviceKinds() {
        return ACCEPTED_DEVICE_KINDS;
    }

    public static long capacityFromBaseAndModuleFe(long baseCapacityFe, long moduleCapacityFe) {
        long base = Math.max(0L, baseCapacityFe);
        long module = Math.max(0L, moduleCapacityFe);
        return module > 0L ? module : base;
    }
}
