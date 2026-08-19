package com.moakiee.ae2lt.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class CreativePigmeeDuplicationRecipeContractTest {
    @Test
    void copiesOneNonPigmeeTargetAndReturnsTheCatalyst() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/recipe/CreativePigmeeDuplicationRecipe.java"));

        assertTrue(source.contains("private static final int OUTPUT_COUNT = 64"));
        assertTrue(source.contains("target.copyWithCount(OUTPUT_COUNT)"));
        assertTrue(source.contains("boolean foundCatalyst = false"));
        assertTrue(source.contains("stack.is(ModFumos.CREATIVE_PIGMEE_FUMO_ITEM.get())"));
        assertTrue(source.contains("if (foundCatalyst)"));
        assertTrue(source.contains("getRemainingItems(CraftingInput input)"));
        assertTrue(source.contains("remaining.set(slot, stack.copyWithCount(1))"));
        assertFalse(source.contains("getMaxStackSize"));
    }

    @Test
    void convertsKnownProgressionTargetsBeforeFallingBackToDuplication() throws Exception {
        String creativeSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/recipe/CreativePigmeeDuplicationRecipe.java"));
        String conversionSource = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/recipe/PigmeeConversionLogic.java"));

        assertTrue(conversionSource.contains("target.is(Items.LIGHTNING_ROD)"));
        assertTrue(conversionSource.contains("CellOutcome.HIGH_VOLTAGE"));
        assertTrue(conversionSource.contains("target.is(ModItems.THUNDERSTORM_CONDENSATE.get())"));
        assertTrue(conversionSource.contains("CellOutcome.EXTREME_HIGH_VOLTAGE"));
        assertTrue(conversionSource.contains("target.is(ModItems.LIGHTNING_COLLAPSE_MATRIX.get())"));
        assertTrue(conversionSource.contains("CellOutcome.LIGHTNING_COLLAPSE_MATRIX"));
        assertTrue(conversionSource.contains("target.is(ModItems.BULK_LIGHTNING_STORAGE_COMPONENT.get())"));
        assertTrue(conversionSource.contains("new ItemStack(ModItems.INFINITE_STORAGE_CELL.get())"));
        assertTrue(conversionSource.contains("target.is(ModBlocks.OVERLOAD_SUPERCOMPUTING_UNIT.asItem())"));
        assertTrue(conversionSource.contains(
                "new ItemStack(ModBlocks.MULTIDIMENSIONAL_SUPERCOMPUTING_UNIT.asItem())"));
        assertTrue(conversionSource.contains(
                "target.is(ModBlocks.MATTER_WARPING_MATRIX_OVERLOAD_MAIN_CORE.asItem())"));
        assertTrue(conversionSource.contains(
                "new ItemStack(ModBlocks.MATTER_WARPING_MATRIX_MULTIDIMENSIONAL_MAIN_CORE.asItem())"));
        assertTrue(conversionSource.contains(
                "target.is(ModItems.RAILGUN_MODULE_OVERLOAD_EXECUTION.get())"));
        assertTrue(conversionSource.contains(
                "new ItemStack(ModItems.RAILGUN_MODULE_MULTIDIMENSIONAL_EXECUTION.get())"));
        assertTrue(conversionSource.contains(
                "target.is(ModItems.CELESTWEAVE_SUBMODULE_PHASE_SHIELD.get())"));
        assertTrue(conversionSource.contains(
                "new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_MULTIDIMENSIONAL_PROTECTION.get())"));
        assertTrue(creativeSource.indexOf("PigmeeConversionLogic.createResult(target)")
                < creativeSource.indexOf("target.copyWithCount(OUTPUT_COUNT)"));
        assertFalse(conversionSource.contains("HYPERDIMENSIONAL_PIGMEE_FUMO_ITEM"));
    }

    @Test
    void registersOnlyTheHiddenDuplicationBehaviorNotAnAcquisitionRecipe() throws Exception {
        String registry = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/registry/ModRecipeTypes.java"));
        var recipe = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/ae2lt/recipe/creative_pigmee_duplication.json")))
                .getAsJsonObject();

        assertTrue(registry.contains("CREATIVE_PIGMEE_DUPLICATION_SERIALIZER"));
        assertTrue(registry.contains("new SimpleCraftingRecipeSerializer<>("
                + "CreativePigmeeDuplicationRecipe::new)"));
        assertEquals("ae2lt:creative_pigmee_duplication", recipe.get("type").getAsString());
        assertFalse(Files.exists(Path.of(
                "src/main/resources/data/ae2lt/recipe/creative_pigmee_fumo.json")));
    }

    @Test
    void tooltipKeepsTheReferenceSubtleAndExplainsCatalystSemantics() throws Exception {
        String translations = Files.readString(Path.of(
                "src/main/resources/assets/ae2lt/lang/zh_cn.json"));

        assertTrue(translations.contains("\"block.ae2lt.creative_pigmee_fumo\": \"创造猪咪\""));
        assertTrue(translations.contains("猪咪不会消失"));
        assertTrue(translations.contains("镜子里的自己"));
        assertFalse(translations.contains("致敬砧板之尘"));
    }
}
