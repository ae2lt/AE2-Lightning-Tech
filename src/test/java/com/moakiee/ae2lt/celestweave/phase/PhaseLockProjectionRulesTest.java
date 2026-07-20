package com.moakiee.ae2lt.celestweave.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import org.junit.jupiter.api.Test;

class PhaseLockProjectionRulesTest {
    @Test
    void everyProjectionOnlyAcceptsItsOwnArmorInventorySlot() {
        var armorSlots = new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };

        for (EquipmentSlot expected : armorSlots) {
            int inventorySlot = Inventory.INVENTORY_SIZE + expected.getIndex();
            assertEquals(inventorySlot, PhaseLockProjectionRules.expectedInventorySlot(expected));
            assertTrue(PhaseLockProjectionRules.isExpectedSlot(expected, inventorySlot));
            assertFalse(PhaseLockProjectionRules.isExpectedSlot(expected, 0));
            for (EquipmentSlot other : armorSlots) {
                if (other != expected) {
                    assertFalse(PhaseLockProjectionRules.isExpectedSlot(
                            expected,
                            Inventory.INVENTORY_SIZE + other.getIndex()));
                }
            }
        }
    }

    @Test
    void nonArmorSlotsAreNeverAccepted() {
        assertEquals(-1, PhaseLockProjectionRules.expectedInventorySlot(EquipmentSlot.MAINHAND));
        assertFalse(PhaseLockProjectionRules.isExpectedSlot(EquipmentSlot.MAINHAND, 0));
    }
}
