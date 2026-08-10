package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CelestweaveEnchantingContractTest {
    private static final Path JAVA_ROOT = Path.of("src/main/java/com/moakiee/ae2lt/celestweave");

    @Test
    void fePoweredArmorCanUseTheEnchantingTableWithoutVanillaDurability() throws Exception {
        String armorItem = Files.readString(JAVA_ROOT.resolve("BaseCelestweaveArmorItem.java"));
        String armorMaterial = Files.readString(JAVA_ROOT.resolve("CelestweaveArmorMaterials.java"));

        assertTrue(armorItem.contains("public boolean isEnchantable(ItemStack stack)"));
        assertTrue(armorItem.contains("return true;"));
        assertFalse(armorItem.contains(".durability("),
                "FE-powered armor must remain free of vanilla durability");
        assertTrue(armorMaterial.contains("ENCHANTMENT_VALUE = 32"));
        assertTrue(armorMaterial.contains("return ENCHANTMENT_VALUE;"));
    }
}
