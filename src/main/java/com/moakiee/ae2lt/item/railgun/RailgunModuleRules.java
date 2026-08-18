package com.moakiee.ae2lt.item.railgun;

import java.util.List;
import java.util.Set;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.energy.LightningCompensationPolicy;

/** Pure installation and capability rules kept independent from Minecraft item bootstrap. */
final class RailgunModuleRules {
    private static final Set<DeviceKind> RAILGUN_ONLY = Set.of(DeviceKind.RAILGUN);
    private static final Set<DeviceKind> CORE_ACCEPTS = Set.of(
            DeviceKind.RAILGUN,
            DeviceKind.CELESTWEAVE_CORE);

    private RailgunModuleRules() {
    }

    static int maxInstallAmount(RailgunModuleType type) {
        return switch (type) {
            case CORE, OVERLOAD_EXECUTION -> 1;
            case COMPUTE, ACCELERATION, RANGE -> 2;
        };
    }

    static Set<DeviceKind> acceptableDevices(RailgunModuleType type) {
        return type == RailgunModuleType.CORE ? CORE_ACCEPTS : RAILGUN_ONLY;
    }

    static DeviceSlotType acceptableSlot(RailgunModuleType type) {
        return switch (type) {
            case CORE -> DeviceSlotType.CORE;
            case COMPUTE -> DeviceSlotType.COMPUTE;
            case ACCELERATION -> DeviceSlotType.ACCELERATION;
            case RANGE -> DeviceSlotType.RANGE;
            case OVERLOAD_EXECUTION -> DeviceSlotType.OVERLOAD_EXECUTION;
        };
    }

    static boolean accepts(RailgunModuleType type, DeviceKind deviceKind, DeviceSlotType slotType) {
        if (type == RailgunModuleType.CORE && deviceKind == DeviceKind.CELESTWEAVE_CORE) {
            return slotType == DeviceSlotType.CHEST_MODULE;
        }
        return deviceKind == DeviceKind.RAILGUN && slotType == acceptableSlot(type);
    }

    static List<DeviceCapability> capabilitiesFor(RailgunModuleType type) {
        return switch (type) {
            case CORE -> List.of(new DeviceCapability.LightningCompensation(
                    LightningCompensationPolicy.DEFAULT_HIGH_VOLTAGE_PER_EXTREME_HIGH_VOLTAGE));
            case COMPUTE -> List.of(
                    new DeviceCapability.ChainTuning(2, 1, 0),
                    new DeviceCapability.PulseTuning(1.5D, 1.0D));
            case ACCELERATION -> List.of(new DeviceCapability.AccelerationFactor(0.30D));
            case RANGE -> List.of(new DeviceCapability.RangeMultiplier(2.0D));
            case OVERLOAD_EXECUTION -> List.of(new DeviceCapability.OverloadExecutionTuning(0.02D, 200, 8));
        };
    }
}
