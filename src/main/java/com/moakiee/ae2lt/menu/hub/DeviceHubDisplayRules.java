package com.moakiee.ae2lt.menu.hub;

import java.util.List;

public final class DeviceHubDisplayRules {
    private static final int RAILGUN_MODULE_SLOT_COUNT = 6;

    private DeviceHubDisplayRules() {
    }

    public static boolean powerAvailable(long storedFe, boolean gridReachable, boolean appFluxOnline) {
        return storedFe > 0L || (gridReachable && appFluxOnline);
    }

    public static String armorStatusKey(
            boolean hasCore,
            boolean locked,
            boolean overloaded,
            boolean powerAvailable) {
        if (!hasCore) {
            return "ae2lt.device_hub.status.missing_core";
        }
        if (locked) {
            return "ae2lt.device_hub.status.locked";
        }
        if (overloaded) {
            return "ae2lt.device_hub.status.overloaded";
        }
        if (!powerAvailable) {
            return "ae2lt.device_hub.status.unpowered";
        }
        return "ae2lt.device_hub.status.normal";
    }

    public static String moduleStateKey(boolean enabled, boolean active) {
        if (!enabled) {
            return "ae2lt.device_hub.module.state.disabled";
        }
        return active
                ? "ae2lt.device_hub.module.state.active"
                : "ae2lt.device_hub.module.state.offline";
    }

    public static int clampScrollOffset(int requested, int itemCount, int visibleRows) {
        int max = Math.max(0, itemCount - Math.max(0, visibleRows));
        return Math.max(0, Math.min(requested, max));
    }

    public static int countModuleUnits(List<Integer> moduleCounts) {
        int total = 0;
        for (int count : moduleCounts) {
            total += Math.max(0, count);
        }
        return total;
    }

    public static int railgunModuleSlotCount() {
        return RAILGUN_MODULE_SLOT_COUNT;
    }
}
