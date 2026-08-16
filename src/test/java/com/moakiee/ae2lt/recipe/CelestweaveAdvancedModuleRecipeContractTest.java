package com.moakiee.ae2lt.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class CelestweaveAdvancedModuleRecipeContractTest {
    private static final Path LIGHTNING_ASSEMBLY_RECIPES =
            Path.of("src/main/resources/data/ae2lt/recipe/lightning_assembly");

    @Test
    void phaseShieldRequiresSixteenEntangledTopologicalLattices() throws Exception {
        JsonObject recipe = JsonParser.parseString(Files.readString(
                LIGHTNING_ASSEMBLY_RECIPES.resolve("module_phase_shield.json")))
                .getAsJsonObject();

        int latticeCount = recipe.getAsJsonArray("inputs").asList().stream()
                .map(element -> element.getAsJsonObject())
                .filter(input -> "ae2lt:entangled_topological_lattice".equals(
                        input.getAsJsonObject("ingredient").get("item").getAsString()))
                .mapToInt(input -> input.get("count").getAsInt())
                .sum();

        assertEquals(16, latticeCount);
    }
}
