package com.moakiee.ae2lt.device;

// Slot taxonomy shared across overload devices. Each device picks its own subset.
public enum DeviceSlotType {
    CORE,
    HEAD_MODULE,
    CHEST_MODULE,
    LEGS_MODULE,
    FEET_MODULE,
    COMPUTE,
    ACCELERATION,
    OVERLOAD_EXECUTION
}
