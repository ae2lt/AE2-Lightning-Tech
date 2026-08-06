package com.moakiee.ae2lt.celestweave.phase;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;

/** Pure slot rules shared by the projection item and tests. */
public final class PhaseLockProjectionRules {
    private PhaseLockProjectionRules() {
    }

    public static int expectedInventorySlot(EquipmentSlot slot) {
        if (slot == null || slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
            return -1;
        }
        return Inventory.INVENTORY_SIZE + slot.getIndex();
    }

    public static boolean isExpectedSlot(EquipmentSlot slot, int slotId) {
        return slotId == expectedInventorySlot(slot);
    }
}
