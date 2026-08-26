package com.moakiee.ae2lt.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SiliconBlockCompatibilityContractTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void siliconRecipesAreDisabledWhenExtendedAeIsLoaded() throws IOException {
        assertDisabledWithExtendedAe("silicon_block.json");
        assertDisabledWithExtendedAe("silicon_decompress.json");
    }

    @Test
    void siliconBlockTagEntriesAreOptional() throws IOException {
        assertOptionalSiliconBlock("data/forge/tags/items/storage_blocks/silicon.json");
        assertOptionalSiliconBlock("data/forge/tags/blocks/storage_blocks/silicon.json");
        assertOptionalSiliconBlock("data/minecraft/tags/blocks/mineable/pickaxe.json");
        assertOptionalSiliconBlock("data/minecraft/tags/blocks/needs_stone_tool.json");
    }

    @Test
    void registrationUsesTheForgeExtendedAeModId() throws IOException {
        String blocks = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/registry/ModBlocks.java"));
        String mod = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/AE2LightningTech.java"));

        assertTrue(blocks.contains("EXTENDEDAE_MODID = \"expatternprovider\""));
        assertTrue(blocks.contains("ModBlocks::shouldRegisterSiliconBlock"));
        assertTrue(mod.contains("if (ModBlocks.hasSiliconBlock())"));
    }

    @Test
    void optionalBlockLeavesNoUnconditionalLootTableOrGuideIndex() throws IOException {
        assertFalse(Files.exists(RESOURCES.resolve("data/ae2lt/loot_tables/blocks/silicon_block.json")));

        String guide = Files.readString(RESOURCES.resolve(
                "assets/ae2lt/ae2guide/materials/overload-machine-frame.md"));
        String localizedGuide = Files.readString(RESOURCES.resolve(
                "assets/ae2lt/ae2guide/_zh_cn/materials/overload-machine-frame.md"));
        assertFalse(guide.contains("- ae2lt:silicon_block"));
        assertFalse(localizedGuide.contains("- ae2lt:silicon_block"));
    }

    private static void assertDisabledWithExtendedAe(String filename) throws IOException {
        JsonObject recipe = readJson("data/ae2lt/recipes/" + filename);
        JsonObject not = recipe.getAsJsonArray("conditions").get(0).getAsJsonObject();
        JsonObject loaded = not.getAsJsonObject("value");

        assertEquals("forge:not", not.get("type").getAsString(), filename);
        assertEquals("forge:mod_loaded", loaded.get("type").getAsString(), filename);
        assertEquals("expatternprovider", loaded.get("modid").getAsString(), filename);
    }

    private static void assertOptionalSiliconBlock(String relativePath) throws IOException {
        JsonObject tag = readJson(relativePath);
        boolean found = tag.getAsJsonArray("values").asList().stream()
                .filter(element -> element.isJsonObject())
                .map(element -> element.getAsJsonObject())
                .anyMatch(entry -> "ae2lt:silicon_block".equals(entry.get("id").getAsString())
                        && entry.has("required")
                        && !entry.get("required").getAsBoolean());

        assertTrue(found, relativePath);
        assertFalse(tag.getAsJsonArray("values").asList().stream()
                .anyMatch(element -> element.isJsonPrimitive()
                        && "ae2lt:silicon_block".equals(element.getAsString())), relativePath);
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        return JsonParser.parseString(Files.readString(RESOURCES.resolve(relativePath))).getAsJsonObject();
    }
}
