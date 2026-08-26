package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FirmamentNaturalLootContractTest {
    @Test
    void naturalLootUsesTheOutputInsertionPath() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/FirmamentConversionCoreBlockEntity.java"));
        String method = source.substring(
                source.indexOf("public void initializeNaturalLoot("),
                source.indexOf("private Optional<FirmamentConversionLockedRecipe> lockCurrentRecipe("));

        assertTrue(method.contains("inventory.insertRecipeOutput("),
                "Natural loot must bypass the input-only slot validation through the output API");
        assertFalse(method.contains("inventory.setStackInSlot("),
                "Validated direct writes reject every output slot");
    }
}
