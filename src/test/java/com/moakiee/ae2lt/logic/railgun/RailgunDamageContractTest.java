package com.moakiee.ae2lt.logic.railgun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RailgunDamageContractTest {
    @Test
    void configuredArmorPenetrationIsAppliedOnce() {
        assertEquals(52.0D, DamageContext.finalDamage(100.0D, 0.40D, 0.80D), 0.00001D);
        assertEquals(84.0D, DamageContext.finalDamage(100.0D, 0.80D, 0.80D), 0.00001D);
    }

    @Test
    void electromagneticDamageSkipsVanillaArmorAfterManualAdjustment() throws Exception {
        String bypassTag = Files.readString(Path.of(
                "src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json"));
        assertTrue(bypassTag.contains("\"ae2lt:electromagnetic\""));

        String defaults = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/config/RailgunDefaults.java"));
        assertFalse(defaults.contains("ARMOR_BYPASS_"));
    }
}
