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
    void pigmeeCoreAndBothPigmeeTechnologyAdvancementsCoexist() throws Exception {
        String pigBrainOverload = Files.readString(Path.of(
                "src/main/resources/data/ae2lt/advancement/main/pig_brain_overload.json"));
        String pigmeeTechnology = Files.readString(Path.of(
                "src/main/resources/data/ae2lt/advancement/main/pigmee_technology.json"));
        String pigZip = Files.readString(Path.of(
                "src/main/resources/data/ae2lt/advancement/main/pig_zip.json"));
        String translations = Files.readString(Path.of(
                "src/main/resources/assets/ae2lt/lang/zh_cn.json"));

        assertTrue(pigBrainOverload.contains("\"parent\": \"ae2lt:main/pig_zip\""));
        assertTrue(pigBrainOverload.contains("\"trigger\": \"minecraft:recipe_crafted\""));
        assertTrue(pigBrainOverload.contains("\"recipe_id\": \"ae2lt:pigmee_mentalmath_unit\""));
        assertTrue(pigBrainOverload.contains("\"hidden\": false"));

        assertTrue(pigmeeTechnology.contains("\"parent\": \"ae2lt:main/pig_zip\""));
        assertTrue(pigmeeTechnology.contains("\"recipe_id\": \"ae2lt:pigmee_pattern_provider\""));
        assertTrue(pigmeeTechnology.contains("\"recipe_id\": \"ae2lt:pigmee_mentalmath_unit\""));
        assertTrue(pigmeeTechnology.contains("\"recipe_id\": \"ae2lt:pigmee_storage_cell_from_housing\""));

        assertTrue(pigZip.contains("\"id\": \"ae2lt:pigmee_core\""));
        assertTrue(pigZip.contains("\"trigger\": \"minecraft:inventory_changed\""));
        assertTrue(pigZip.contains("\"items\": \"ae2lt:pigmee_core\""));
        assertTrue(translations.contains("\"advancements.ae2lt.pig_zip.title\": \"猪.zip\""));
        assertTrue(translations.contains("这只可爱的小猪被你的铁砧砸成了饼，你为什么要这样做?"));
        assertTrue(translations.contains("\"advancements.ae2lt.pig_brain_overload.title\": \"猪脑过载\""));
        assertTrue(translations.contains("\"advancements.ae2lt.pigmee_technology.title\": \"猪咪科技\""));
    }

    @Test
    void jeiExplainsHowToObtainThePigmeeCore() throws Exception {
        String jeiPlugin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/integration/jei/JEIPlugin.java"));
        String translations = Files.readString(Path.of(
                "src/main/resources/assets/ae2lt/lang/zh_cn.json"));

        assertTrue(jeiPlugin.contains("registration.addIngredientInfo("));
        assertTrue(jeiPlugin.contains("ModItems.PIGMEE_CORE.get()"));
        assertTrue(jeiPlugin.contains("\"jei.ae2lt.pigmee_core.info\""));
        assertTrue(translations.contains("\"jei.ae2lt.pigmee_core.info\""));
        assertTrue(translations.contains("过载水晶块"));
        assertTrue(translations.contains("下落的铁砧"));
    }

    private static JsonObject readRecipe(String filename) throws Exception {
        return JsonParser.parseString(Files.readString(RECIPE_ROOT.resolve(filename))).getAsJsonObject();
    }

}
