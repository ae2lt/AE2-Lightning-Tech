package com.moakiee.ae2lt.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class PigmeeRecipeContractTest {
    private static final Path RECIPE_ROOT = Path.of("src/main/resources/data/ae2lt/recipe");
    private static final List<String> PIGMEE_TECH_RECIPES = List.of(
            "pigmee_pattern_provider.json",
            "pigmee_molecular_assembler.json",
            "pigmee_mentalmath_unit.json",
            "pigmee_item_cell_housing.json",
            "pigmee_storage_component.json",
            "pigmee_storage_cell_from_housing.json");

    @Test
    void pigmeeTechDependsOnlyOnPigmeesAndOrdinaryHelperTools() throws Exception {
        for (String filename : PIGMEE_TECH_RECIPES) {
            String recipe = Files.readString(RECIPE_ROOT.resolve(filename));
            assertFalse(recipe.contains("_processor\""), filename);
            assertFalse(recipe.contains("\"ae2:crafting_unit\""), filename);
            assertFalse(recipe.contains("\"ae2:crafting_accelerator\""), filename);
            assertFalse(recipe.contains("\"ae2:"), filename);
        }
    }

    @Test
    void pigmeeStorageUsesAChestAndASimpleNonPigmeeHousing() throws Exception {
        JsonObject component = readRecipe("pigmee_storage_component.json");
        String componentJson = component.toString();
        assertTrue(componentJson.contains("ae2lt:pigmee_fumo"));
        assertTrue(componentJson.contains("minecraft:chest"));
        assertEquals(2, component.getAsJsonArray("ingredients").size());

        JsonObject housing = readRecipe("pigmee_item_cell_housing.json");
        String housingJson = housing.toString();
        assertTrue(housingJson.contains("minecraft:pink_wool"));
        assertTrue(housingJson.contains("minecraft:glass_pane"));
        assertTrue(housingJson.contains("minecraft:iron_nugget"));
        assertFalse(housingJson.contains("ae2lt:pigmee_fumo"));
    }

    @Test
    void mentalmathUnitUsesPigmeeStorageAndPhysicalControls() throws Exception {
        JsonObject recipe = readRecipe("pigmee_mentalmath_unit.json");
        String json = recipe.toString();
        assertTrue(json.contains("ae2lt:pigmee_storage_component"));
        assertTrue(json.contains("minecraft:stone_button"));
        assertTrue(json.contains("minecraft:lever"));
        assertTrue(json.contains("minecraft:stone_pressure_plate"));
        assertEquals(4, recipe.getAsJsonArray("ingredients").size());
    }

    private static JsonObject readRecipe(String filename) throws Exception {
        return JsonParser.parseString(Files.readString(RECIPE_ROOT.resolve(filename))).getAsJsonObject();
    }

}
