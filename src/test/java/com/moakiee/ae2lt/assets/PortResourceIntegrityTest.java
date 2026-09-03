package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

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

    @Test
    void textureAnimationFramesReferenceExistingSprites() throws IOException {
        List<String> invalid = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(ASSETS.resolve("textures"))) {
            for (Path metadata : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".png.mcmeta"))
                    .toList()) {
                validateAnimationFrames(metadata, invalid);
            }
        }

        assertTrue(invalid.isEmpty(), String.join(System.lineSeparator(), invalid));
    }

    @Test
    void bundledGuiTexturesStayInTheAe2ltNamespace() throws IOException {
        Path ae2Textures = RESOURCES.resolve(Path.of("assets", "ae2", "textures"));
        if (Files.isDirectory(ae2Textures)) {
            try (Stream<Path> paths = Files.walk(ae2Textures)) {
                assertTrue(paths.filter(Files::isRegularFile).findAny().isEmpty(),
                        "Bundled textures under assets/ae2 can override AE2 or another add-on");
            }
        }

        Path ae2ltGuis = ASSETS.resolve(Path.of("textures", "guis"));
        Path interfaceTexture = ae2ltGuis.resolve("overloaded_interface.png");
        Path providerTexture = ae2ltGuis.resolve("overloaded_pattern_provider.png");
        assertTrue(Files.isRegularFile(interfaceTexture));
        assertTrue(Files.isRegularFile(providerTexture));
        assertEquals("40e24e9e63598eeb8f9504643cf79116c868e8626ae152c1fa3d5849480316f0",
                sha256(interfaceTexture));
        assertEquals("81064beb4d84ccf8f041f78282e5c82f7749f9f1807a635b03a1b3cfe8b8c0b5",
                sha256(providerTexture));

        String interfaceScreen = Files.readString(
                RESOURCES.resolve(Path.of("assets", "ae2lt", "screens", "overloaded_interface.json")));
        String providerScreen = Files.readString(
                RESOURCES.resolve(Path.of("assets", "ae2lt", "screens", "overloaded_pattern_provider.json")));
        assertTrue(interfaceScreen.contains("ae2lt:textures/guis/overloaded_interface.png"));
        assertTrue(providerScreen.contains("ae2lt:textures/guis/overloaded_pattern_provider.png"));
        assertFalse(interfaceScreen.contains("guis/ex_interface.png"));
        assertFalse(providerScreen.contains("guis/ex_pattern_provider.png"));
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 must be available", exception);
        }
    }

    private static void validateAnimationFrames(Path metadata, List<String> invalid) throws IOException {
        String metadataName = metadata.getFileName().toString();
        Path texture = metadata.resolveSibling(metadataName.substring(0, metadataName.length() - ".mcmeta".length()));
        if (!Files.isRegularFile(texture)) {
            invalid.add(metadata + " -> missing texture " + texture.getFileName());
            return;
        }

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(metadata)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        if (!root.has("animation") || !root.get("animation").isJsonObject()) {
            return;
        }

        BufferedImage image = ImageIO.read(texture.toFile());
        if (image == null) {
            invalid.add(metadata + " -> unreadable texture " + texture.getFileName());
            return;
        }

        JsonObject animation = root.getAsJsonObject("animation");
        int frameWidth = animation.has("width") ? animation.get("width").getAsInt() : image.getWidth();
        int frameHeight = animation.has("height") ? animation.get("height").getAsInt() : frameWidth;
        if (frameWidth <= 0 || frameHeight <= 0
                || image.getWidth() % frameWidth != 0 || image.getHeight() % frameHeight != 0) {
            invalid.add(metadata + " -> frame size " + frameWidth + "x" + frameHeight
                    + " does not divide texture size " + image.getWidth() + "x" + image.getHeight());
            return;
        }

        int frameCount = image.getWidth() / frameWidth * (image.getHeight() / frameHeight);
        if (!animation.has("frames") || !animation.get("frames").isJsonArray()) {
            return;
        }
        for (JsonElement frame : animation.getAsJsonArray("frames")) {
            JsonElement index = frame.isJsonObject() ? frame.getAsJsonObject().get("index") : frame;
            if (index == null || !index.isJsonPrimitive() || !index.getAsJsonPrimitive().isNumber()) {
                invalid.add(metadata + " -> invalid frame declaration " + frame);
                continue;
            }
            int value = index.getAsInt();
            if (value < 0 || value >= frameCount) {
                invalid.add(metadata + " -> frame " + value + " outside 0.." + (frameCount - 1));
            }
        }
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
