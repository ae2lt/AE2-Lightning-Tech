package com.moakiee.ae2lt.logic;

import appeng.api.networking.ticking.TickRateModulation;

public final class OverloadedInterfaceTickDecider {
    private static final int ALL_NORMAL_DIRECTIONS = 6;

    private OverloadedInterfaceTickDecider() {
    }

    public static boolean hasGridItemIoWork(
            boolean wirelessMode,
            boolean hasImportBuffer,
            boolean hasWirelessConnections,
            boolean importAuto,
            boolean exportAuto) {
        if (hasImportBuffer) {
            return true;
        }
        if (wirelessMode) {
            return hasWirelessConnections && (importAuto || exportAuto);
        }
        return importAuto || exportAuto;
    }

    public static boolean hasServerEnergyWork(
            boolean wirelessMode,
            boolean hasWirelessConnections,
            boolean hasEnergyOutput,
            boolean hasFeKey,
            boolean hasInductionCard) {
        return ((wirelessMode && hasWirelessConnections) || hasEnergyOutput)
                && hasFeKey
                && hasInductionCard;
    }

    public static TickRateModulation gridTickModulation(
            boolean hasItemIoWork,
            boolean craftingCardInstalled,
            boolean craftingDidWork) {
        if (hasItemIoWork || craftingDidWork) {
            return TickRateModulation.URGENT;
        }
        return craftingCardInstalled
                ? TickRateModulation.SLOWER
                : TickRateModulation.SLEEP;
    }

    public static int normalIoDirectionCount(boolean hasConfiguredDirection) {
        return hasConfiguredDirection ? 1 : ALL_NORMAL_DIRECTIONS;
    }

    public static boolean shouldRegisterEjectPorts(boolean wirelessMode, boolean ejectMode) {
        return wirelessMode && ejectMode;
    }
}
