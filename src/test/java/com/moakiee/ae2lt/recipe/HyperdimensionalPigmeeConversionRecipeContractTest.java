package com.moakiee.ae2lt.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class HyperdimensionalPigmeeConversionRecipeContractTest {
    @Test
    void spendsItsConversionAndReturnsAsAnOrdinaryPigmee() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/recipe/HyperdimensionalPigmeeConversionRecipe.java"));

        assertTrue(source.contains("stack.is(ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO_ITEM.get())"));
        assertTrue(source.contains("PigmeeConversionLogic.canConvert(target)"));
        assertTrue(source.contains("PigmeeConversionLogic.createResult(findTarget(input))"));
        assertFalse(source.contains("copyWithCount"));
        assertTrue(source.contains("getRemainingItems(CraftingInput input)"));
        assertTrue(source.contains("new ItemStack(ModFumos.PIGMEE_FUMO_ITEM.get())"));
    }

    @Test
    void registersTheItemBlockAndSpecialRecipe() throws Exception {
        String fumos = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/registry/ModFumos.java"));
        String registry = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/registry/ModRecipeTypes.java"));
        String blockEntities = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/registry/ModBlockEntities.java"));
        String creativeTab = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/AE2LightningTech.java"));
        String renderers = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/ModEntityRenderers.java"));
        var recipe = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/ae2lt/recipe/hyperdimensional_pigmee_conversion.json")))
                .getAsJsonObject();

        assertTrue(fumos.contains("\"hyperdimensional_pigmee_fumo\""));
        assertTrue(registry.contains("HYPERDIMENSIONAL_PIGMEE_CONVERSION_SERIALIZER"));
        assertTrue(registry.contains("HyperdimensionalPigmeeConversionRecipe::new"));
        assertTrue(blockEntities.contains("ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO.get()"));
        assertFalse(creativeTab.contains("output.accept(ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO_ITEM.get())"));
        assertTrue(renderers.contains("wrapFumoItemModel(event, \"hyperdimensional_pigmee_fumo\")"));
        assertEquals("ae2lt:hyperdimensional_pigmee_conversion", recipe.get("type").getAsString());
        assertTrue(Files.exists(Path.of(
                "src/main/resources/assets/ae2lt/blockstates/hyperdimensional_pigmee_fumo.json")));
        assertTrue(Files.exists(Path.of(
                "src/main/resources/assets/ae2lt/models/item/hyperdimensional_pigmee_fumo.json")));
        assertTrue(Files.exists(Path.of(
                "src/main/resources/data/ae2lt/loot_table/blocks/hyperdimensional_pigmee_fumo.json")));
    }

    @Test
    void describesItsOneUseConversionOnlyRole() throws Exception {
        String translations = Files.readString(Path.of(
                "src/main/resources/assets/ae2lt/lang/zh_cn.json"));

        assertTrue(translations.contains(
                "\"block.ae2lt.hyperdimensional_pigmee_fumo\": \"超维猪咪\""));
        assertTrue(translations.contains("一只猪咪在时空中走了一遭"));
        assertTrue(translations.contains("把复制万物的能力遗落在了彼端"));
        assertTrue(translations.contains("仅残余最后一次跨越界限的力量"));
        assertTrue(translations.contains("它会变回最初的猪咪"));
    }

    @Test
    void completingAConversionGrantsTheHiddenTruePigmeeTechnologyAdvancement() throws Exception {
        String advancement = Files.readString(Path.of(
                "src/main/resources/data/ae2lt/advancement/main/true_pigmee_technology.json"));

        assertTrue(advancement.contains("\"parent\": \"ae2lt:main/pigmee_technology\""));
        assertTrue(advancement.contains("\"trigger\": \"minecraft:recipe_crafted\""));
        assertTrue(advancement.contains(
                "\"recipe_id\": \"ae2lt:hyperdimensional_pigmee_conversion\""));
        assertTrue(advancement.contains("\"hidden\": true"));
    }
}
