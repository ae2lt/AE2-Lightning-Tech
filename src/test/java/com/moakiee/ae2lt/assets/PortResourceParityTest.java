package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PortResourceParityTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    void overloadedPowerSupplyHasItsRegisteredBlockDrop() throws IOException {
        var lootTable = RESOURCES.resolve(Path.of(
                "data", "ae2lt", "loot_tables", "blocks", "overloaded_power_supply.json"));

        assertTrue(Files.isRegularFile(lootTable));
        var json = Files.readString(lootTable);
        assertTrue(json.contains("\"name\": \"ae2lt:overloaded_power_supply\""));
        assertTrue(json.contains("\"condition\": \"minecraft:survives_explosion\""));
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
