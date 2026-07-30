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
    void functionalBlocksUseFullThreeByThreeWorkbenchLayouts() throws Exception {
        for (String filename : List.of(
                "pigmee_pattern_provider.json",
                "pigmee_molecular_assembler.json",
                "pigmee_mentalmath_unit.json",
                "pigmee_item_cell_housing.json",
                "pigmee_storage_component.json")) {
            JsonObject recipe = readRecipe(filename);
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), filename);
            assertEquals(3, recipe.getAsJsonArray("pattern").size(), filename);
            recipe.getAsJsonArray("pattern").forEach(
                    row -> assertEquals(3, row.getAsString().length(), filename));
        }
    }

    @Test
    void pigmeeStorageLooksLikeStorageAndTheHousingLooksLikeAHousing() throws Exception {
        JsonObject component = readRecipe("pigmee_storage_component.json");
        String componentJson = component.toString();
        assertTrue(componentJson.contains("ae2lt:pigmee_fumo"));
        assertTrue(componentJson.contains("minecraft:chest"));
        assertTrue(componentJson.contains("minecraft:stone_button"));

        JsonObject housing = readRecipe("pigmee_item_cell_housing.json");
        String housingJson = housing.toString();
        assertTrue(housingJson.contains("minecraft:pink_wool"));
        assertTrue(housingJson.contains("minecraft:glass_pane"));
        assertTrue(housingJson.contains("minecraft:iron_nugget"));
        assertFalse(housingJson.contains("ae2lt:pigmee_fumo"));
    }

    @Test
    void mentalmathUnitIsAFullPhysicalControlPanel() throws Exception {
        JsonObject recipe = readRecipe("pigmee_mentalmath_unit.json");
        String json = recipe.toString();
        assertTrue(json.contains("ae2lt:pigmee_storage_component"));
        assertTrue(json.contains("minecraft:stone_button"));
        assertTrue(json.contains("minecraft:lever"));
        assertTrue(json.contains("minecraft:stone_pressure_plate"));
        assertEquals("BBB", recipe.getAsJsonArray("pattern").get(0).getAsString());
        assertEquals("LCL", recipe.getAsJsonArray("pattern").get(1).getAsString());
        assertEquals("PPP", recipe.getAsJsonArray("pattern").get(2).getAsString());
    }

    @Test
    void craftingAnyPigmeeTechnologyRecipeGrantsTheAdvancement() throws Exception {
        String advancement = Files.readString(Path.of(
                "src/main/resources/data/ae2lt/advancement/main/pigmee_technology.json"));

        assertTrue(advancement.contains("\"trigger\": \"minecraft:recipe_crafted\""));
        for (String filename : PIGMEE_TECH_RECIPES) {
            String recipeId = filename.substring(0, filename.length() - ".json".length());
            assertTrue(advancement.contains("\"recipe_id\": \"ae2lt:" + recipeId + "\""), filename);
        }
        assertTrue(advancement.contains("\"hidden\": false"));
    }

    private static JsonObject readRecipe(String filename) throws Exception {
        return JsonParser.parseString(Files.readString(RECIPE_ROOT.resolve(filename))).getAsJsonObject();
    }

}
