package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

final class CelestweaveEnchantingContractTest {
    private static final Path JAVA_ROOT = Path.of("src/main/java/com/moakiee/ae2lt/celestweave");
    private static final Path ITEM_TAG_ROOT = Path.of("src/main/resources/data/minecraft/tags/item");
    private static final Path ENCHANTABLE_TAG_ROOT = ITEM_TAG_ROOT.resolve("enchantable");

    @Test
    void fePoweredArmorCanUseTheEnchantingTableWithoutVanillaDurability() throws Exception {
        String armorItem = Files.readString(JAVA_ROOT.resolve("BaseCelestweaveArmorItem.java"));
        String armorMaterial = Files.readString(JAVA_ROOT.resolve("CelestweaveArmorMaterials.java"));

        assertTrue(armorItem.contains("public boolean isEnchantable(ItemStack stack)"));
        assertTrue(armorItem.contains("return true;"));
        assertFalse(armorItem.contains(".durability("), "FE-powered armor must remain free of vanilla durability");
        assertTrue(armorMaterial.contains("ENCHANTMENT_VALUE = 32"));
        assertTrue(armorMaterial.contains("ENCHANTMENT_VALUE,"));
    }

    @Test
    void everyArmorPieceBelongsToItsVanillaEnchantableSlotTag() throws Exception {
        Map<String, String> expectedTags = Map.of(
                "head_armor.json", "ae2lt:celestweave_oculus",
                "chest_armor.json", "ae2lt:celestweave_core",
                "leg_armor.json", "ae2lt:celestweave_conduit",
                "foot_armor.json", "ae2lt:celestweave_stride");

        for (var entry : expectedTags.entrySet()) {
            String itemId = "\"" + entry.getValue() + "\"";
            String equipmentTag = Files.readString(ITEM_TAG_ROOT.resolve(entry.getKey()));
            String enchantableTag = Files.readString(ENCHANTABLE_TAG_ROOT.resolve(entry.getKey()));
            assertTrue(equipmentTag.contains(itemId),
                    entry.getValue() + " should be present in the equipment tag " + entry.getKey());
            assertTrue(enchantableTag.contains(itemId),
                    entry.getValue() + " should be present in the enchantable tag " + entry.getKey());
        }
    }

    @Test
    void armorDoesNotOfferDurabilityOrMendingEnchantments() throws Exception {
        String durabilityTag = Files.readString(ENCHANTABLE_TAG_ROOT.resolve("durability.json"));

        assertTrue(durabilityTag.contains("\"remove\""));
        assertTrue(durabilityTag.contains("\"ae2lt:celestweave_oculus\""));
        assertTrue(durabilityTag.contains("\"ae2lt:celestweave_core\""));
        assertTrue(durabilityTag.contains("\"ae2lt:celestweave_conduit\""));
        assertTrue(durabilityTag.contains("\"ae2lt:celestweave_stride\""));
    }
}
