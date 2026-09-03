package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class ScreenStyleNamespaceContractTest {
    private static final Path MAIN = Path.of("src", "main");
    private static final Path AE2LT_SCREENS = MAIN.resolve(Path.of(
            "resources", "assets", "ae2lt", "screens"));
    private static final Path AE2_SCREENS = MAIN.resolve(Path.of(
            "resources", "assets", "ae2", "screens"));
    private static final Path JAVA = MAIN.resolve(Path.of("java", "com", "moakiee", "ae2lt"));
    private static final Set<String> EXTERNAL_INCLUDES = Set.of(
            "ae2:screens/common/common.json",
            "ae2:screens/common/player_inventory.json",
            "ae2:screens/terminals/pattern_encoding_terminal.json",
            "ae2:screens/wtlib/universal_terminal_with_viewcells.json");

    @Test
    void ae2ltOwnsEveryBundledScreenStyle() throws IOException {
        assertFalse(Files.exists(AE2_SCREENS),
                "AE2LT screen styles must not be published in AE2's resource namespace");

        List<Path> styles;
        try (Stream<Path> paths = Files.walk(AE2LT_SCREENS)) {
            styles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList();
        }
        assertEquals(31, styles.size());

        List<String> invalid = new ArrayList<>();
        for (Path style : styles) {
            JsonObject json;
            try (Reader reader = Files.newBufferedReader(style)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (!json.has("includes")) {
                continue;
            }
            for (var element : json.getAsJsonArray("includes")) {
                String include = element.getAsString();
                if (include.contains(":")) {
                    if (!EXTERNAL_INCLUDES.contains(include)) {
                        invalid.add(AE2LT_SCREENS.relativize(style) + " -> unexpected external include " + include);
                    }
                    continue;
                }

                Path target = style.getParent().resolve(include).normalize();
                if (!target.startsWith(AE2LT_SCREENS) || !Files.isRegularFile(target)) {
                    invalid.add(AE2LT_SCREENS.relativize(style) + " -> missing local include " + include);
                }
            }
        }
        assertTrue(invalid.isEmpty(), String.join(System.lineSeparator(), invalid));
    }

    @Test
    void directAndSubScreenLoadsUseExplicitNamespaces() throws IOException {
        String registrations = Files.readString(JAVA.resolve("client/ModScreens.java"));
        assertEquals(16, count(registrations, "AE2LTStyleManager.loadStyleDoc("));
        assertEquals(16, count(registrations, "ae2lt:screens/"));
        assertFalse(registrations.contains("import appeng.client.gui.style.StyleManager;"));
        assertFalse(registrations.contains("var style = StyleManager.loadStyleDoc("));

        List<String> invalid = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(JAVA.resolve("client"))) {
            for (Path source : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                String java = Files.readString(source);
                if (!java.contains("extends AESubScreen")) {
                    continue;
                }
                if (source.getFileName().toString().equals("TianshuSetProcessingPatternAmountScreen.java")) {
                    assertTrue(java.contains("super(parentScreen, \"/screens/set_processing_pattern_amount.json\")"));
                    continue;
                }
                if (java.contains("\"/screens/") || !java.contains("ae2lt:screens/")) {
                    invalid.add(source.toString());
                }
            }
        }
        assertTrue(invalid.isEmpty(), "AE2LT sub-screens without an ae2lt style path: " + invalid);
    }

    @Test
    void loaderPreservesAe2MergeAndIncludeBoundaries() throws IOException {
        String loader = Files.readString(JAVA.resolve("client/gui/AE2LTStyleManager.java"));
        for (String property : List.of("slots", "text", "palette", "images", "terminalStyle", "widgets")) {
            assertTrue(loader.contains("\"" + property + "\""), property);
        }
        assertTrue(loader.contains("loadedStyles.add(normalizedId)"));
        assertTrue(loader.contains("resolveInclude(normalizedId, include)"));
        assertTrue(loader.contains("new ResourceLocation(source.getNamespace(), parent + include)"));
        assertTrue(loader.contains("ScreenStyle.GSON.fromJson(json, ScreenStyle.class)"));
        assertTrue(loader.contains("style.validate()"));
        assertTrue(loader.contains("styleCache.clear()"));
    }

    @Test
    void aeSubScreenBridgeIsClientOnlyAndFallsBackToAe2() throws IOException {
        String mixin = Files.readString(JAVA.resolve("mixin/client/AESubScreenStyleMixin.java"));
        String config = Files.readString(MAIN.resolve("resources/ae2lt.mixins.json"));

        assertTrue(mixin.contains("@Mixin(value = AESubScreen.class, remap = false)"));
        assertTrue(mixin.contains("method = \"<init>\""));
        assertTrue(mixin.contains(
                "Lappeng/client/gui/style/StyleManager;loadStyleDoc(Ljava/lang/String;)Lappeng/client/gui/style/ScreenStyle;"));
        assertTrue(mixin.contains("if (AE2LTStyleManager.handles(path))"));
        assertTrue(mixin.contains("return StyleManager.loadStyleDoc(path);"));
        assertTrue(mixin.contains("private ScreenStyle ae2lt$loadNamespacedStyle(String path)"));
        assertFalse(mixin.contains("private static ScreenStyle ae2lt$loadNamespacedStyle"));

        int clientArray = config.indexOf("\"client\": [");
        int injectorConfig = config.indexOf("\"injectors\":", clientArray);
        int bridge = config.indexOf("\"client.AESubScreenStyleMixin\"");
        assertTrue(clientArray >= 0 && bridge > clientArray && bridge < injectorConfig);
        assertEquals(1, count(config, "client.AESubScreenStyleMixin"));
    }

    private static int count(String value, String needle) {
        int result = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            result++;
            offset += needle.length();
        }
        return result;
    }
}
