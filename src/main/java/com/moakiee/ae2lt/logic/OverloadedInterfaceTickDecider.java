package com.moakiee.ae2lt.logic;

public final class OverloadedInterfaceTickDecider {
    private OverloadedInterfaceTickDecider() {
    }

    public static boolean hasServerTickWork(
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

    public static boolean shouldRegisterEjectPorts(boolean wirelessMode, boolean ejectMode) {
        return wirelessMode && ejectMode;
    }
}
