package com.moakiee.ae2lt.logic.tianshu.maintenance;

/** UI-independent four-state projection used by the future terminal item-list badge. */
public enum InventoryMaintenanceBadge {
    GREEN,
    YELLOW,
    RED,
    GRAY;

    public static InventoryMaintenanceBadge from(InventoryMaintenanceStatus status) {
        if (status == null) return YELLOW;
        return switch (status) {
            case DISABLED -> GRAY;
            case SATISFIED -> GREEN;
            case MISSING_PATTERN, MISSING_INGREDIENTS, OFFLINE -> RED;
            default -> YELLOW;
        };
    }
}
