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
    void conditionallyRegisteredPowerSupplyUsesARegistrySafeLootTable() throws IOException {
        var lootTable = RESOURCES.resolve(Path.of(
                "data", "ae2lt", "loot_tables", "blocks", "overloaded_power_supply.json"));
        var blockSource = Path.of(
                "src", "main", "java", "com", "moakiee", "ae2lt", "block",
                "OverloadedPowerSupplyBlock.java");

        assertTrue(Files.isRegularFile(lootTable));
        var json = Files.readString(lootTable);
        assertTrue(json.contains("\"type\": \"minecraft:dynamic\""),
                "The loot table must not resolve the AppFlux-gated BlockItem while AppFlux is absent");
        assertTrue(json.contains("\"name\": \"ae2lt:overloaded_power_supply\""));
        assertTrue(json.contains("\"condition\": \"minecraft:survives_explosion\""));
        assertFalse(json.contains("\"type\": \"minecraft:item\""));

        var source = Files.readString(blockSource);
        assertFalse(source.contains("noLootTable()"));
        assertTrue(source.contains("builder.withDynamicDrop(BLOCK_ITEM_DROP"));
        assertTrue(source.contains("return super.getDrops(state, builder);"),
                "Drops must retain the vanilla loot-table and Forge global-modifier pipeline");
    }

    @Test
    void powerSupplyPersistsAndDropsItsInstalledCellThroughAe2Hooks() throws IOException {
        var blockEntitySource = Path.of(
                "src", "main", "java", "com", "moakiee", "ae2lt", "blockentity",
                "OverloadedPowerSupplyBlockEntity.java");

        var source = Files.readString(blockEntitySource);
        assertTrue(source.contains("void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops)"));
        assertTrue(source.contains("logic.flushBufferToNetwork();"));
        assertTrue(source.contains("AppFluxBridge.persistCellStorage(cachedCellView);"));
        assertTrue(source.contains("drops.add(cell.copy());"));
        assertTrue(source.contains("void clearContent()"));
        assertTrue(source.contains("cellInv.clear();"));
    }

}
