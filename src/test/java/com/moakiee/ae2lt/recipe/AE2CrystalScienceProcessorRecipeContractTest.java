package com.moakiee.ae2lt.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class AE2CrystalScienceProcessorRecipeContractTest {
    private static final Path OVERLOAD_PROCESSING_RECIPES =
            Path.of("src/main/resources/data/ae2lt/recipes/overload_processing");

    @Test
    void crystalScienceProcessorsUseTheirNativeBulkIngredients() throws Exception {
        assertRecipe(
                "ae2cs_simple_processor.json",
                "ae2cs:simple_processor",
                List.of(
                        new ExpectedInput("item", "minecraft:quartz_block", 9),
                        new ExpectedInput("tag", "forge:storage_blocks/redstone", 4),
                        new ExpectedInput("tag", "forge:storage_blocks/silicon", 4)));
        assertRecipe(
                "ae2cs_resonating_processor.json",
                "ae2cs:resonating_processor",
                List.of(
                        new ExpectedInput(
                                "tag", "forge:storage_blocks/pure_crystal/resonating_crystal_block", 4),
                        new ExpectedInput(
                                "tag", "forge:storage_blocks/pure_crystal/meteor_crystal_block", 4),
                        new ExpectedInput("tag", "forge:storage_blocks/silicon", 4)));
    }

    private static void assertRecipe(String filename, String resultId, List<ExpectedInput> expectedInputs)
            throws Exception {
        JsonObject recipe = JsonParser.parseString(
                        Files.readString(OVERLOAD_PROCESSING_RECIPES.resolve(filename)))
                .getAsJsonObject();

        JsonObject condition = recipe.getAsJsonArray("conditions").get(0).getAsJsonObject();
        assertEquals("forge:mod_loaded", condition.get("type").getAsString(), filename);
        assertEquals("ae2cs", condition.get("modid").getAsString(), filename);
        assertEquals("ae2lt:overload_processing", recipe.get("type").getAsString(), filename);
        assertEquals(400_000, recipe.get("totalEnergy").getAsInt(), filename);
        assertEquals(1, recipe.get("lightningCost").getAsInt(), filename);
        assertEquals("high_voltage", recipe.get("lightningTier").getAsString(), filename);

        assertEquals(expectedInputs.size(), recipe.getAsJsonArray("inputs").size(), filename);
        for (int index = 0; index < expectedInputs.size(); index++) {
            ExpectedInput expected = expectedInputs.get(index);
            JsonObject input = recipe.getAsJsonArray("inputs").get(index).getAsJsonObject();
            assertEquals(
                    expected.id(),
                    input.getAsJsonObject("ingredient").get(expected.kind()).getAsString(),
                    filename);
            assertEquals(expected.count(), input.get("count").getAsInt(), filename);
        }

        JsonObject result = recipe.getAsJsonArray("results").get(0).getAsJsonObject();
        assertEquals(resultId, result.get("id").getAsString(), filename);
        assertEquals(36, result.get("count").getAsInt(), filename);
    }

    private record ExpectedInput(String kind, String id, int count) {}
}
