package com.moakiee.ae2lt.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class DocumentedAdvancementContractTest {
    private static final Path ADVANCEMENT_ROOT =
            Path.of("src/main/resources/data/ae2lt/advancement/main");

    @Test
    void overloadTntRequiresBothCraftingAndPlayerIgnition() throws Exception {
        JsonObject advancement = read("overload_tnt_annihilation");
        JsonObject criteria = advancement.getAsJsonObject("criteria");

        assertEquals(
                "ae2lt:overload_tnt_from_gunpowder",
                criteria.getAsJsonObject("craft_overload_tnt")
                        .getAsJsonObject("conditions")
                        .get("recipe_id")
                        .getAsString());
        assertEquals(
                "minecraft:impossible",
                criteria.getAsJsonObject("ignite_overload_tnt").get("trigger").getAsString());
        assertEquals(2, advancement.getAsJsonArray("requirements").size());

        String block = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/block/OverloadTntBlock.java"));
        assertTrue(block.contains("igniter instanceof ServerPlayer"));
        assertTrue(block.contains("main/overload_tnt_annihilation"));
        assertTrue(block.contains("\"ignite_overload_tnt\""));
    }

    @Test
    void mysteriousCellBranchesMatchTheirExactCellTypes() throws Exception {
        assertCustomData("construct_fragment", "get_infinite_lightning_rod_cell", "{CellType:0b}");
        assertCustomData("thunderstorm_generator", "get_infinite_high_voltage_cell", "{CellType:1b}");
        assertCustomData(
                "thunderstorm_generator",
                "get_infinite_extreme_high_voltage_cell",
                "{CellType:2b}");
        assertCustomData(
                "observable_black_hole",
                "get_infinite_collapse_matrix_cell",
                "{CellType:3b}");
        assertEquals(2, read("thunderstorm_generator").getAsJsonArray("requirements").size());
    }

    @Test
    void pairedCollectionAdvancementsRequireBothItems() throws Exception {
        JsonObject fufus = read("author_fufus");
        assertEquals("ae2lt:main/neutralization", fufus.get("parent").getAsString());
        assertEquals(2, fufus.getAsJsonArray("requirements").size());

        JsonObject multidimensional = read("multidimensional_unfolding");
        assertEquals(
                "ae2lt:main/hyperdimensional_pigmee",
                multidimensional.get("parent").getAsString());
        assertEquals(2, multidimensional.getAsJsonArray("requirements").size());
    }

    @Test
    void selectedPigmeeProgressionKeepsBothEntryAdvancements() throws Exception {
        JsonObject brainOverload = read("pigmee_brain_overload");
        assertEquals("ae2lt:main/pig_zip", brainOverload.get("parent").getAsString());
        assertEquals(
                "ae2lt:pigmee_mentalmath_unit",
                brainOverload.getAsJsonObject("criteria")
                        .getAsJsonObject("craft_mentalmath_unit")
                        .getAsJsonObject("conditions")
                        .get("recipe_id")
                        .getAsString());

        assertTrue(Files.exists(ADVANCEMENT_ROOT.resolve("pigmee_technology.json")));
        assertEquals(
                "ae2lt:main/pig_zip",
                read("hyperdimensional_pigmee").get("parent").getAsString());
    }

    @Test
    void selectedChineseTitlesArePresent() throws Exception {
        String chinese = Files.readString(
                Path.of("src/main/resources/assets/ae2lt/lang/zh_cn.json"));

        assertTrue(chinese.contains("\"advancements.ae2lt.radiation_assimilation.title\": \"身在辐中不知福\""));
        assertTrue(chinese.contains("\"advancements.ae2lt.pigmee_brain_overload.title\": \"猪脑过载\""));
        assertTrue(chinese.contains("\"advancements.ae2lt.pigmee_technology.title\": \"猪咪科技\""));
        assertTrue(chinese.contains("\"advancements.ae2lt.hyperdimensional_pigmee.title\": \"这是……猪咪？！\""));
    }

    private static void assertCustomData(
            String advancementName,
            String criterionName,
            String expectedPredicate) throws Exception {
        JsonObject criterion = read(advancementName)
                .getAsJsonObject("criteria")
                .getAsJsonObject(criterionName);
        JsonObject item = criterion.getAsJsonObject("conditions")
                .getAsJsonArray("items")
                .get(0)
                .getAsJsonObject();

        assertEquals("ae2lt:mysterious_cell", item.get("items").getAsString());
        assertEquals(
                expectedPredicate,
                item.getAsJsonObject("predicates")
                        .get("minecraft:custom_data")
                        .getAsString());
    }

    private static JsonObject read(String name) throws Exception {
        return JsonParser.parseString(
                Files.readString(ADVANCEMENT_ROOT.resolve(name + ".json")))
                .getAsJsonObject();
    }
}
