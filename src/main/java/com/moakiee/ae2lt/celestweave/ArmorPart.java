package com.moakiee.ae2lt.celestweave;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;

public enum ArmorPart {
    HEAD(
            DeviceKind.CELESTWEAVE_OCULUS,
            DeviceSlotType.HEAD_MODULE,
            "head",
            4,
            48),
    CHEST(
            DeviceKind.CELESTWEAVE_CORE,
            DeviceSlotType.CHEST_MODULE,
            "chest",
            5,
            128),
    LEGS(
            DeviceKind.CELESTWEAVE_CONDUIT,
            DeviceSlotType.LEGS_MODULE,
            "legs",
            4,
            96),
    FEET(
            DeviceKind.CELESTWEAVE_STRIDE,
            DeviceSlotType.FEET_MODULE,
            "feet",
            4,
            64);

    private final DeviceKind deviceKind;
    private final DeviceSlotType moduleSlot;
    private final String equipmentSlotName;
    private final int moduleSlotCount;
    private final int dynamicCap;

    ArmorPart(
            DeviceKind deviceKind,
            DeviceSlotType moduleSlot,
            String equipmentSlotName,
            int moduleSlotCount,
            int dynamicCap) {
        this.deviceKind = deviceKind;
        this.moduleSlot = moduleSlot;
        this.equipmentSlotName = equipmentSlotName;
        this.moduleSlotCount = moduleSlotCount;
        this.dynamicCap = dynamicCap;
    }

    public DeviceKind deviceKind() {
        return deviceKind;
    }

    public DeviceSlotType moduleSlot() {
        return moduleSlot;
    }

    public String equipmentSlotName() {
        return equipmentSlotName;
    }

    public int moduleSlotCount() {
        return moduleSlotCount;
    }

    public int dynamicCap() {
        return dynamicCap;
    }
}
