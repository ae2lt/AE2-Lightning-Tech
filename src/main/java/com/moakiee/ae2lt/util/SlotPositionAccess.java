package com.moakiee.ae2lt.util;

import net.minecraft.world.inventory.Slot;

/**
 * Centralizes the slot moves used by menus and screens. The 1.20.1 access transformer
 * makes these fields mutable, matching AE2's own Forge implementation.
 */
public final class SlotPositionAccess {
    private SlotPositionAccess() {
    }

    public static void set(Slot slot, int x, int y) {
        slot.x = x;
        slot.y = y;
    }
}
