package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class LootTableCompatibilityContractTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data");
    private static final Path LOOT_TABLES = DATA.resolve(Path.of("ae2lt", "loot_tables"));
    private static final Path BLOCK_LOOT_TABLES = LOOT_TABLES.resolve("blocks");
    private static final List<String> SILK_TOUCH_TABLES = List.of(
            "cracked_budding_overload_crystal.json",
            "damaged_budding_overload_crystal.json",
            "flawed_budding_overload_crystal.json",
            "large_overload_crystal_bud.json",
            "medium_overload_crystal_bud.json",
            "overload_crystal_cluster.json",
            "small_overload_crystal_bud.json");
    private static final Set<String> SUPPORTED_ENTRY_TYPES = Set.of(
            "minecraft:alternatives",
            "minecraft:dynamic",
            "minecraft:item");
    private static final Set<String> SUPPORTED_CONDITIONS = Set.of(
            "forge:loot_table_id",
            "minecraft:match_tool",
            "minecraft:random_chance",
            "minecraft:survives_explosion");
    private static final Set<String> SUPPORTED_FUNCTIONS = Set.of(
            "minecraft:apply_bonus",
            "minecraft:explosion_decay",
            "minecraft:set_count");

    @Test
    void everyLootResourceUsesOnlyTheAuditedMinecraft120Schema() throws IOException {
        List<String> problems = new ArrayList<>();
        int tableCount = 0;

        try (Stream<Path> paths = Files.walk(LOOT_TABLES)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".json"))
                    .toList()) {
                tableCount++;
                inspectForPost120Schema(path, readJson(path), problems);
            }
        }

        Path modifier = DATA.resolve(Path.of(
                "ae2lt", "loot_modifiers", "inactive_firmament_spirit_core_end_city.json"));
        inspectForPost120Schema(modifier, readJson(modifier), problems);

        assertEquals(69, tableCount, "The compatibility audit must cover every AE2LT loot table");
        assertTrue(problems.isEmpty(), String.join(System.lineSeparator(), problems));
    }

    @Test
    void silkTouchPredicatesUseTheMinecraft120Format() throws IOException {
        for (String filename : SILK_TOUCH_TABLES) {
            JsonObject predicate = silkTouchPredicate(readTable(filename));
            assertFalse(predicate.has("predicates"), filename + " uses the 1.21+ item predicate format");

            JsonObject enchantment = predicate.getAsJsonArray("enchantments")
                    .get(0)
                    .getAsJsonObject();
            assertEquals("minecraft:silk_touch", enchantment.get("enchantment").getAsString(), filename);
            assertEquals(1, enchantment.getAsJsonObject("levels").get("min").getAsInt(), filename);
        }
    }

    @Test
    void matureClusterDropsFourOverloadCrystalsWithoutSilkTouch() throws IOException {
        JsonObject table = readTable("overload_crystal_cluster.json");
        JsonObject normalDrop = table.getAsJsonArray("pools")
                .get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject()
                .getAsJsonArray("children").get(1).getAsJsonObject();

        assertEquals("ae2lt:overload_crystal", normalDrop.get("name").getAsString());
        assertEquals(4, normalDrop.getAsJsonArray("functions")
                .get(0).getAsJsonObject()
                .get("count").getAsInt());
        assertEquals("minecraft:fortune", normalDrop.getAsJsonArray("functions")
                .get(1).getAsJsonObject()
                .get("enchantment").getAsString());
    }

    private static void inspectForPost120Schema(Path path, JsonElement element, List<String> problems) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> inspectForPost120Schema(path, child, problems));
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        for (String post120Key : List.of("components", "predicates")) {
            if (object.has(post120Key)) {
                problems.add(path + ": uses post-1.20 field '" + post120Key + "'");
            }
        }
        validateDiscriminator(path, object, "condition", SUPPORTED_CONDITIONS, problems);
        validateDiscriminator(path, object, "function", SUPPORTED_FUNCTIONS, problems);
        if (object.has("type") && object.get("type").isJsonPrimitive()) {
            String type = object.get("type").getAsString();
            if (!type.equals("minecraft:block")
                    && !type.equals("minecraft:chest")
                    && !type.equals("minecraft:uniform")
                    && !type.equals("ae2lt:add_item")
                    && !SUPPORTED_ENTRY_TYPES.contains(type)) {
                problems.add(path + ": unaudited loot type '" + type + "'");
            }
        }
        object.asMap().values().forEach(child -> inspectForPost120Schema(path, child, problems));
    }

    private static void validateDiscriminator(
            Path path,
            JsonObject object,
            String field,
            Set<String> supported,
            List<String> problems) {
        if (object.has(field)) {
            String value = object.get(field).getAsString();
            if (!supported.contains(value)) {
                problems.add(path + ": unaudited loot " + field + " '" + value + "'");
            }
        }
    }

    private static JsonObject readTable(String filename) throws IOException {
        return readJson(BLOCK_LOOT_TABLES.resolve(filename));
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static JsonObject silkTouchPredicate(JsonObject table) {
        return table.getAsJsonArray("pools")
                .get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject()
                .getAsJsonArray("children").get(0).getAsJsonObject()
                .getAsJsonArray("conditions").get(0).getAsJsonObject()
                .getAsJsonObject("predicate");
    }
}
