package com.moakiee.ae2lt.machine.firmament;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FirmamentConversionRecipeLookupSourceContractTest {
    @Test
    void lockedRecipeLookupDoesNotScanEveryFirmamentRecipe() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/machine/firmament/recipe/FirmamentConversionRecipeService.java"));
        String lookup = source.substring(
                source.indexOf("findRecipeById("),
                source.indexOf("findLockedRecipeMatch("));

        assertTrue(lookup.contains(".byKey(recipeId)"));
        assertTrue(lookup.contains("instanceof FirmamentConversionRecipe"));
        assertTrue(lookup.contains("ModRecipeTypes.FIRMAMENT_CONVERSION_TYPE.get()"));
        assertFalse(lookup.contains("getAllRecipesFor("));
    }
}
