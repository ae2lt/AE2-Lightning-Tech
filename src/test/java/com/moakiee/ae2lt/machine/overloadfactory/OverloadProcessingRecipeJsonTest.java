package com.moakiee.ae2lt.machine.overloadfactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OverloadProcessingRecipeJsonTest {

    @Test
    void overloadProcessingInputsUseLegacyIngredientStringShape() throws IOException {
        var recipeDir = Path.of(
                "src",
                "main",
                "resources",
                "data",
                "ae2lt",
                "recipe",
                "overload_processing");

        try (var paths = Files.list(recipeDir)) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var text = Files.readString(path);
                assertTrue(!text.contains("\"ingredient\": {"),
                        () -> path.getFileName() + " uses an ingredient shape unsupported by 26.1.2");
            }
        }
    }
}
