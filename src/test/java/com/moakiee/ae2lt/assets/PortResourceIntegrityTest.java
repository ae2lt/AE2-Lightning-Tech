package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class PortResourceIntegrityTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path GENERATED_RESOURCES = Path.of("src", "generated", "resources");
    private static final Path ASSETS = RESOURCES.resolve(Path.of("assets", "ae2lt"));

    @Test
    void everyJsonResourceParses() throws IOException {
        List<String> failures = new ArrayList<>();
        int parsed = 0;

        for (Path root : List.of(RESOURCES, GENERATED_RESOURCES)) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile)
                        .filter(PortResourceIntegrityTest::isJsonResource)
                        .toList()) {
                    parsed++;
                    try (Reader reader = Files.newBufferedReader(path)) {
                        JsonParser.parseReader(reader);
                    } catch (RuntimeException exception) {
                        failures.add(path + ": " + exception.getMessage());
                    }
                }
            }
        }

        assertTrue(parsed >= 800, "Unexpectedly small resource set: " + parsed);
        assertTrue(failures.isEmpty(), String.join(System.lineSeparator(), failures));
    }

    @Test
    void englishAndChineseTranslationsHaveIdenticalKeys() throws IOException {
        Set<String> english = languageKeys("en_us.json");
        Set<String> chinese = languageKeys("zh_cn.json");

        assertEquals(english, chinese);
        assertTrue(english.size() >= 1_300, "Unexpectedly small translation set");
    }

    @Test
    void internalModelAndTextureReferencesResolve() throws IOException {
        List<String> missing = new ArrayList<>();
        Path models = ASSETS.resolve("models");

        try (Stream<Path> paths = Files.walk(models)) {
            for (Path source : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList()) {
                JsonObject model;
                try (Reader reader = Files.newBufferedReader(source)) {
                    model = JsonParser.parseReader(reader).getAsJsonObject();
                }

                if (model.has("parent")) {
                    validateModelReference(source, model.get("parent").getAsString(), missing);
                }
                collectModelReferences(source, model, missing);
                if (model.has("textures")) {
                    for (JsonElement texture : model.getAsJsonObject("textures").asMap().values()) {
                        validateTextureReference(source, texture.getAsString(), missing);
                    }
                }
            }
        }

        assertTrue(missing.isEmpty(), String.join(System.lineSeparator(), missing));
    }

    private static void collectModelReferences(Path source, JsonElement element, List<String> missing) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectModelReferences(source, child, missing));
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        for (var entry : element.getAsJsonObject().entrySet()) {
            if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive()) {
                validateModelReference(source, entry.getValue().getAsString(), missing);
            } else {
                collectModelReferences(source, entry.getValue(), missing);
            }
        }
    }

    private static void validateModelReference(Path source, String reference, List<String> missing) {
        String path = ae2ltPath(reference);
        if (path == null) {
            return;
        }
        int variantSeparator = path.indexOf('#');
        if (variantSeparator >= 0) {
            path = path.substring(0, variantSeparator);
        }
        Path target = ASSETS.resolve("models").resolve(path + ".json");
        if (!Files.isRegularFile(target)) {
            missing.add(source + " -> missing model " + reference);
        }
    }

    private static void validateTextureReference(Path source, String reference, List<String> missing) {
        if (reference.startsWith("#")) {
            return;
        }
        String path = ae2ltPath(reference);
        if (path == null) {
            return;
        }
        Path target = ASSETS.resolve("textures").resolve(path + ".png");
        if (!Files.isRegularFile(target)) {
            missing.add(source + " -> missing texture " + reference);
        }
    }

    private static String ae2ltPath(String reference) {
        int separator = reference.indexOf(':');
        if (separator < 0 || !reference.substring(0, separator).equals("ae2lt")) {
            return null;
        }
        return reference.substring(separator + 1);
    }

    private static Set<String> languageKeys(String fileName) throws IOException {
        try (Reader reader = Files.newBufferedReader(ASSETS.resolve("lang").resolve(fileName))) {
            return new HashSet<>(JsonParser.parseReader(reader).getAsJsonObject().keySet());
        }
    }

    private static boolean isJsonResource(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".json") || name.endsWith(".mcmeta");
    }
}
