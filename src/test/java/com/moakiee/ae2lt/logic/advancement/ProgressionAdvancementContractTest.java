package com.moakiee.ae2lt.logic.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProgressionAdvancementContractTest {
    private static final Path ADVANCEMENT_ROOT =
            Path.of("src/main/resources/data/ae2lt/advancement/main");

    @Test
    void documentProgressionUsesTheRequestedParentsFramesAndVisibility() throws Exception {
        assertDisplay("radiation_assimilation", "ae2lt:main/root", "challenge", true);
        assertDisplay("annihilation", "ae2lt:main/overload_singularity", "task", false);

        assertDisplay("neutralization", "ae2lt:main/lightning_collapse_matrix", "goal", true);
        assertDisplay("construct_fragment", "ae2lt:main/neutralization", "goal", false);
        assertDisplay("author_fufu", "ae2lt:main/neutralization", "goal", false);
        assertDisplay("reconstructed_truth", "ae2lt:main/neutralization", "challenge", true);

        assertDisplay("pig_zip", "ae2lt:main/root", "task", false);
        assertDisplay("pig_brain_overload", "ae2lt:main/pig_zip", "task", false);
        assertDisplay("pigmee_technology", "ae2lt:main/pig_zip", "task", false);
        assertDisplay(
                "hyperdimensional_pigmee",
                "ae2lt:main/lightning_collapse_matrix",
                "challenge",
                true);
        assertDisplay(
                "true_pigmee_technology",
                "ae2lt:main/pigmee_technology",
                "challenge",
                true);
        assertDisplay(
                "multidimensional_expansion",
                "ae2lt:main/hyperdimensional_pigmee",
                "challenge",
                true);
        assertDisplay(
                "thunderstorm_generator",
                "ae2lt:main/hyperdimensional_pigmee",
                "challenge",
                true);
        assertDisplay(
                "observable_black_hole",
                "ae2lt:main/hyperdimensional_pigmee",
                "challenge",
                false);
        assertTrue(Files.exists(ADVANCEMENT_ROOT.resolve("pigmee_technology.json")));
    }

    @Test
    void overloadTntMustBeCraftedAndPersonallyIgnited() throws Exception {
        JsonObject advancement = advancement("annihilation");
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

        String block = Files.readString(
                Path.of("src/main/java/com/moakiee/ae2lt/block/OverloadTntBlock.java"));
        assertTrue(block.contains("igniter instanceof ServerPlayer player"));
        assertTrue(block.contains("ProgressionAdvancementService.awardOverloadTntIgnited(player)"));
    }

    @Test
    void mysteriousCellVariantsAwardOnlyTheirMatchingBranches() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/advancement/ProgressionAdvancementService.java"));
        String item = Files.readString(
                Path.of("src/main/java/com/moakiee/ae2lt/item/FixedInfiniteCellItem.java"));

        assertTrue(service.contains("if (!FixedInfiniteCellItem.hasType(stack))"));
        assertTrue(service.contains("CONSTRUCT_FRAGMENT, \"obtain_infinite_lightning_rod\""));
        assertTrue(service.contains("THUNDERSTORM_GENERATOR, \"obtain_infinite_high_voltage\""));
        assertTrue(service.contains("THUNDERSTORM_GENERATOR, \"obtain_infinite_extreme_high_voltage\""));
        assertTrue(service.contains("OBSERVABLE_BLACK_HOLE, \"obtain_infinite_collapse_matrix\""));
        assertTrue(item.contains("ProgressionAdvancementService.inspectMysteriousCell(player, stack)"));

        assertEquals(2, advancement("thunderstorm_generator").getAsJsonArray("requirements").size());
        assertEquals(1, advancement("construct_fragment").getAsJsonArray("requirements").size());
        assertEquals(1, advancement("observable_black_hole").getAsJsonArray("requirements").size());
    }

    @Test
    void pairedCollectiblesRequireBothItems() throws Exception {
        JsonObject authors = advancement("author_fufu");
        assertTrue(authors.toString().contains("ae2lt:moakiee_fumo"));
        assertTrue(authors.toString().contains("ae2lt:cystrysu_fumo"));
        assertEquals(2, authors.getAsJsonArray("requirements").size());

        JsonObject cores = advancement("multidimensional_expansion");
        assertTrue(cores.toString().contains("ae2lt:tianshu_multidimensional_main_core"));
        assertTrue(cores.toString().contains("ae2lt:matter_warping_matrix_multidimensional_main_core"));
        assertEquals(2, cores.getAsJsonArray("requirements").size());
    }

    @Test
    void chineseTextMatchesTheSelectedAchievementSet() throws Exception {
        String chinese = Files.readString(
                Path.of("src/main/resources/assets/ae2lt/lang/zh_cn.json"));

        for (String title : new String[]{
                "身在辐中不知福",
                "湮灭",
                "中和",
                "构像残片",
                "这是什么？可爱的fufu？rua一下",
                "构析真理",
                "猪.zip",
                "猪脑过载",
                "猪咪科技",
                "这是……猪咪？？？",
                "真·猪咪科技",
                "多维展开",
                "雷暴发生器",
                "可观测黑洞"}) {
            assertTrue(chinese.contains("\": \"" + title + "\""), title);
        }
    }

    private static void assertDisplay(
            String id,
            String parent,
            String frame,
            boolean hidden) throws Exception {
        JsonObject advancement = advancement(id);
        assertEquals(parent, advancement.get("parent").getAsString(), id);
        JsonObject display = advancement.getAsJsonObject("display");
        assertEquals(frame, display.get("frame").getAsString(), id);
        assertEquals(hidden, display.get("hidden").getAsBoolean(), id);
    }

    private static JsonObject advancement(String id) throws Exception {
        return JsonParser.parseString(
                        Files.readString(ADVANCEMENT_ROOT.resolve(id + ".json")))
                .getAsJsonObject();
    }
}
