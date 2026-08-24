package com.moakiee.ae2lt.machine.firmament;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FirmamentConversionRecipeLookupSourceContractTest {
    @Test
    void lockedRecipeLookupDoesNotScanEveryServerRecipe() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/machine/firmament/recipe/FirmamentConversionRecipeService.java"));

        assertTrue(source.contains("RecipeManagerByTypeAccess.byType("));
        assertTrue(source.contains("RecipeManagerByTypeAccess.findById("));
        assertFalse(source.contains("getRecipeIds()"));
        assertFalse(source.contains("manager.byKey("));
        assertFalse(source.contains("recipesById("));
    }
}
