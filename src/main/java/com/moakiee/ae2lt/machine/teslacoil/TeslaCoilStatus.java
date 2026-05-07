package com.moakiee.ae2lt.machine.teslacoil;

public enum TeslaCoilStatus {
    IDLE("idle"),
    CHARGING("charging"),
    WAITING_FE("waiting_fe"),
    WAITING_INPUTS("waiting_inputs"),
    WAITING_NETWORK("waiting_network"),
    READY("ready");

    private final String serializedName;

    TeslaCoilStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    public static TeslaCoilStatus fromOrdinal(int ordinal) {
        var values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return IDLE;
        }
        return values[ordinal];
    }

    public String translationKey() {
        return "ae2lt.gui.tesla_coil.status." + serializedName;
    }
}
