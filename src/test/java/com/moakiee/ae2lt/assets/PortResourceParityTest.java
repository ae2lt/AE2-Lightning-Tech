package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PortResourceParityTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    void conditionallyRegisteredPowerSupplyUsesItsCodeDefinedDrop() throws IOException {
        var lootTable = RESOURCES.resolve(Path.of(
                "data", "ae2lt", "loot_tables", "blocks", "overloaded_power_supply.json"));
        var blockSource = Path.of(
                "src", "main", "java", "com", "moakiee", "ae2lt", "block",
                "OverloadedPowerSupplyBlock.java");

        assertFalse(Files.exists(lootTable),
                "A loot table cannot reference the AppFlux-gated item when AppFlux is absent");
        var source = Files.readString(blockSource);
        assertTrue(source.contains("noLootTable()"));
        assertTrue(source.contains("List<ItemStack> getDrops"));
    }

    @Test
    void overloadCrystalBlockKeepsTheMainProjectAnimationMetadata() throws IOException {
        var metadata = RESOURCES.resolve(Path.of(
                "assets", "ae2lt", "textures", "block", "overload_crystal_block.png.mcmeta"));

        assertTrue(Files.isRegularFile(metadata));
        var json = Files.readString(metadata);
        assertTrue(json.contains("\"interpolate\": true"));
        assertTrue(json.contains("{\"index\": 0, \"time\": 32}"));
        assertTrue(json.contains("{\"index\": 1, \"time\": 4}"));
    }
}
