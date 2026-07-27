package com.moakiee.ae2lt.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class EasterEggConfigContractTest {
    @Test
    void easterEggControlsAreConfigurableWithoutExplainingTheirOutcome() throws Exception {
        String config = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/config/AE2LTCommonConfig.java"));
        String tnt = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/entity/OverloadTntEntity.java"));
        String cell = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/item/FixedInfiniteCellItem.java"));
        String note = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/research/ResearchNoteGenerator.java"));

        assertTrue(config.contains("define(\"eastereggitem\", \"ae2lt:lightning_collapse_matrix\""));
        assertTrue(config.contains("defineInRange(\"eastereggweight\", 50, 0, 10000)"));
        assertTrue(config.contains("\"eastereggweights\""));
        assertTrue(config.contains("\"avaritia:infinity_ingot=1200\""));
        assertTrue(config.contains("\"minecraft:nether_star=32\""));
        assertTrue(config.contains("return weight >= 1 && weight <= 1_000_000"));
        assertFalse(config.contains("return weight >= 0"));
        assertTrue(config.contains("builder.push(\"easterEgg\")"));
        assertTrue(config.contains(".define(\"enabled\", true)"));
        assertTrue(config.contains("Easter egg item id."));
        assertTrue(config.contains("Easter egg weight."));
        assertTrue(config.contains("Easter egg weights. Format: item_id=weight."));
        assertFalse(config.contains("consume a Lightning Collapse Matrix to drop a Mysterious Cell"));
        assertFalse(config.contains("enableMysteriousCellEasterEgg"));

        assertTrue(tnt.contains("AE2LTCommonConfig.easterEggEnabled()"));
        assertTrue(tnt.contains("AE2LTCommonConfig.easterEggItem()"));
        assertTrue(cell.contains("AE2LTCommonConfig.easterEggWeight()"));
        assertTrue(note.contains("AE2LTCommonConfig.easterEggWeights()"));
        assertTrue(note.contains("new Candidate(entry.getKey(), entry.getValue())"));
        assertTrue(note.contains("AE2LTCommonConfig.isDefaultEasterEggCandidate(id)"));
        assertTrue(note.contains("return itemTranslationKey(id)"));
        assertFalse(note.contains("RANDOM_CANDIDATES"));
    }
}
