package com.moakiee.ae2lt.item.staff;

/** One slot per purpose; the two harvest tiers share a slot. */
public enum StaffModule {
    MATTOCK, SHEARS, KNIFE, WRENCH, NETHERITE, UNRESTRICTED, SPEED, DAMAGE, HARVEST, SMELTING, COLLECTION;

    public int slot() {
        return switch (this) {
            case MATTOCK -> 0;
            case SHEARS -> 1;
            case KNIFE -> 2;
            case WRENCH -> 3;
            case NETHERITE, UNRESTRICTED -> 4;
            case SPEED -> 5;
            case DAMAGE -> 6;
            case HARVEST -> 7;
            case SMELTING -> 8;
            case COLLECTION -> 9;
        };
    }

    public String id() { return "mimicry_module_" + name().toLowerCase(java.util.Locale.ROOT); }
}
