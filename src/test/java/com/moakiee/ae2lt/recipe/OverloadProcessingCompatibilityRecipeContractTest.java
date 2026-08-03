package com.moakiee.ae2lt.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class OverloadProcessingCompatibilityRecipeContractTest {
    private static final Path RECIPE_ROOT = Path.of(
            "src/main/resources/data/ae2lt/recipe/overload_processing");

    @Test
    void advancedAeRecipesKeepUpstreamConventionTags() throws Exception {
        assertTag("aae_shattered_singularity.json", 1, "c:dusts/ender_pearl");
        assertTag("aae_quantum_processor.json", 1, "c:storage_blocks/redstone");
    }

    @Test
    void extendedAeRecipesKeepUpstreamConventionTags() throws Exception {
        assertTag("eae_entro_crystal.json", 0, "c:dusts/entro");
        assertTag("eae_entro_ingot.json", 0, "c:dusts/entro");
        assertTag("eae_entro_ingot.json", 1, "c:ingots/gold");
        assertTag("eae_concurrent_processor.json", 0, "c:storage_blocks/entro");
        assertTag("eae_concurrent_processor.json", 1, "c:storage_blocks/redstone");
    }

    @Test
    void appliedFluxRecipesKeepUpstreamConventionTags() throws Exception {
        assertTag("appflux_redstone_crystal.json", 0, "c:storage_blocks/redstone");
        assertTag("appflux_redstone_crystal.json", 1, "c:gems/fluix");
        assertTag("appflux_redstone_crystal.json", 2, "c:dusts/glowstone");
        assertTag("appflux_harden_insulating_resin.json", 2, "c:silicon");
        assertTag("appflux_harden_insulating_resin.json", 4, "c:dusts/glowstone");
    }

    @Test
    void ae2FluixPearlKeepsUpstreamConventionTags() throws Exception {
        assertTag("ae2_fluix_pearl.json", 0, "c:ender_pearls");
        assertTag("ae2_fluix_pearl.json", 1, "c:dusts/fluix");
    }

    private static void assertTag(String filename, int inputIndex, String expectedTag) throws Exception {
        JsonObject input = recipe(filename).getAsJsonArray("inputs")
                .get(inputIndex)
                .getAsJsonObject();
        JsonObject ingredient = input.getAsJsonObject("ingredient");

        assertEquals(expectedTag, ingredient.get("tag").getAsString(), filename);
        assertFalse(ingredient.has("item"), filename);
    }

    private static JsonObject recipe(String filename) throws Exception {
        return JsonParser.parseString(Files.readString(RECIPE_ROOT.resolve(filename))).getAsJsonObject();
    }
}
