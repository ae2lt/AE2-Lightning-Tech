package com.moakiee.ae2lt.machine.teslacoil;

import net.minecraft.util.StringRepresentable;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.me.key.LightningKey;

public enum TeslaCoilMode implements StringRepresentable {
    HIGH_VOLTAGE("high_voltage"),
    EXTREME_HIGH_VOLTAGE("extreme_high_voltage");

    public static final int PROCESS_TICKS = 5;
    private final String serializedName;

    TeslaCoilMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public static TeslaCoilMode fromOrdinal(int ordinal) {
        return ordinal == EXTREME_HIGH_VOLTAGE.ordinal() ? EXTREME_HIGH_VOLTAGE : HIGH_VOLTAGE;
    }

    public static TeslaCoilMode fromName(String serializedName) {
        for (var value : values()) {
            if (value.serializedName.equals(serializedName)) {
                return value;
            }
        }
        return HIGH_VOLTAGE;
    }

    public TeslaCoilMode next() {
        return this == HIGH_VOLTAGE ? EXTREME_HIGH_VOLTAGE : HIGH_VOLTAGE;
    }

    public long totalEnergy() {
        return this == EXTREME_HIGH_VOLTAGE
                ? AE2LTCommonConfig.teslaCoilExtremeHighVoltageFe()
                : AE2LTCommonConfig.teslaCoilHighVoltageFe();
    }

    public long requiredEnergyForTick(int completedTicks, long consumedEnergy, long totalEnergy) {
        if (completedTicks >= PROCESS_TICKS) {
            return 0L;
        }

        int remainingTicks = Math.max(1, PROCESS_TICKS - completedTicks);
        long remainingEnergy = Math.max(0L, totalEnergy - consumedEnergy);
        if (remainingEnergy <= 0L) {
            return 0L;
        }

        return divideCeil(remainingEnergy, remainingTicks);
    }

    public int requiredDust() {
        return this == HIGH_VOLTAGE ? AE2LTCommonConfig.teslaCoilHighVoltageDustCost() : 0;
    }

    public long requiredHighVoltage() {
        return this == EXTREME_HIGH_VOLTAGE ? AE2LTCommonConfig.teslaCoilExtremeHighVoltageInput() : 0L;
    }

    public LightningKey outputKey() {
        return this == EXTREME_HIGH_VOLTAGE
                ? LightningKey.EXTREME_HIGH_VOLTAGE
                : LightningKey.HIGH_VOLTAGE;
    }

    public String translationKey() {
        return "ae2lt.gui.tesla_coil.mode." + serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    private static long divideCeil(long dividend, long divisor) {
        if (divisor <= 0L) {
            throw new IllegalArgumentException("divisor must be positive");
        }
        return (dividend + divisor - 1L) / divisor;
    }
}
