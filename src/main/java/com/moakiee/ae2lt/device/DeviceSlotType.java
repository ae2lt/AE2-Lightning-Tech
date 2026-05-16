package com.moakiee.ae2lt.device;

// Slot taxonomy shared across overload devices. Each device picks its own subset.
public enum DeviceSlotType {
    CORE,
    COMPUTE,
    ACCELERATION,
    ENERGY,
    OVERLOAD_EXECUTION,
    BUFFER,
    TERMINAL,
    SUBMODULE
}
