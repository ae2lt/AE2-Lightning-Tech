package com.moakiee.ae2lt.logic.tianshu.terminal;

import com.moakiee.ae2lt.logic.tianshu.TianshuFunctionProfile;

/**
 * Server-side capability snapshot for terminal actions. UI state may mirror this later, but action
 * handlers must still validate against a fresh snapshot before changing network or Tianshu state.
 */
public record TianshuTerminalCapabilities(
        boolean hasTianshu,
        boolean closedLoopUploadAvailable,
        boolean inventoryMaintenanceAvailable,
        boolean reservedStockAvailable) {

    public static TianshuTerminalCapabilities withoutTianshu() {
        return new TianshuTerminalCapabilities(false, false, false, false);
    }

    public static TianshuTerminalCapabilities forTianshu(boolean formed, TianshuFunctionProfile profile) {
        if (!formed || profile == null) {
            return withoutTianshu();
        }
        boolean closedLoopReady = profile.supportsClosedLoopPatterns()
                && profile.closedLoopPatternCapacity() > 0
                && profile.supportsClosedLoopSeeds();
        boolean maintenanceReady = profile.supportsInventoryMaintenance();
        return new TianshuTerminalCapabilities(
                true, closedLoopReady, maintenanceReady, maintenanceReady);
    }

    public boolean allows(TianshuTerminalAction action) {
        if (action == null) return false;
        return switch (action) {
            case ENCODE_NORMAL_PATTERN, UPLOAD_NORMAL_PATTERN, ENCODE_CLOSED_LOOP_PATTERN -> true;
            case UPLOAD_CLOSED_LOOP_PATTERN -> closedLoopUploadAvailable;
            case CONFIGURE_INVENTORY_MAINTENANCE -> inventoryMaintenanceAvailable;
            case CONFIGURE_RESERVED_STOCK -> reservedStockAvailable;
        };
    }
}
