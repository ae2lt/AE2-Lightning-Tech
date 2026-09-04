package com.moakiee.ae2lt.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class ProcessorBulkRecipeContractTest {
    private static final Path RECIPE_ROOT =
            Path.of("src/main/resources/data/ae2lt/recipe/overload_processing");

    @Test
    void accumulationProcessorUsesNineFourUnitFluixBlocks() throws Exception {
        JsonObject recipe = recipe("mega_accumulation_processor.json");

        assertInput(recipe, 0, "item", "megacells:sky_steel_block", 4);
        assertInput(recipe, 1, "item", "ae2:fluix_block", 9);
        assertInput(recipe, 2, "tag", "c:storage_blocks/silicon", 4);
        assertResult(recipe, "megacells:accumulation_processor");
    }

    @Test
    void appliedGeneratorsOriginationProcessorUsesEmberBlocks() throws Exception {
        JsonObject recipe = recipe("appgen_origination_processor.json");
        JsonObject condition = recipe.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject();

        assertEquals("neoforge:mod_loaded", condition.get("type").getAsString());
        assertEquals("appgen", condition.get("modid").getAsString());
        assertInput(recipe, 0, "item", "appgen:ember_block", 9);
        assertInput(recipe, 1, "tag", "c:storage_blocks/redstone", 4);
        assertInput(recipe, 2, "tag", "c:storage_blocks/silicon", 4);
        assertResult(recipe, "appgen:origination_processor");
    }

    private static void assertInput(
            JsonObject recipe, int index, String kind, String id, int count) {
        JsonObject input = recipe.getAsJsonArray("inputs").get(index).getAsJsonObject();
        assertEquals(id, input.getAsJsonObject("ingredient").get(kind).getAsString());
        assertEquals(count, input.get("count").getAsInt());
    }

    private static void assertResult(JsonObject recipe, String id) {
        JsonObject result = recipe.getAsJsonArray("results").get(0).getAsJsonObject();
        assertEquals(id, result.get("id").getAsString());
        assertEquals(36, result.get("count").getAsInt());
        assertEquals(400_000, recipe.get("totalEnergy").getAsInt());
        assertEquals(1, recipe.get("lightningCost").getAsInt());
        assertEquals("high_voltage", recipe.get("lightningTier").getAsString());
    }

    private static JsonObject recipe(String filename) throws Exception {
        return JsonParser.parseString(Files.readString(RECIPE_ROOT.resolve(filename))).getAsJsonObject();
    }
}
