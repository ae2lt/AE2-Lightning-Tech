package com.moakiee.ae2lt.recipe;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CreativePigmeeMultidimensionalConversionContractTest {
    @Test
    void upgradesBothCombatModulesBeforeOrdinaryDuplication() throws Exception {
        String recipe = source(
                "src/main/java/com/moakiee/ae2lt/recipe/CreativePigmeeDuplicationRecipe.java");
        String conversion = source(
                "src/main/java/com/moakiee/ae2lt/recipe/PigmeeConversionLogic.java");

        assertTrue(conversion.contains(
                "target.is(ModItems.BULK_LIGHTNING_CELL_COMPONENT.get())"));
        assertTrue(conversion.contains(
                "target.is(ModItems.RAILGUN_MODULE_OVERLOAD_EXECUTION.get())"));
        assertTrue(conversion.contains(
                "new ItemStack(ModItems.RAILGUN_MODULE_MULTIDIMENSIONAL_EXECUTION.get())"));
        assertTrue(conversion.contains(
                "target.is(ModItems.CELESTWEAVE_SUBMODULE_PHASE_SHIELD.get())"));
        assertTrue(conversion.contains(
                "new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_MULTIDIMENSIONAL_PROTECTION.get())"));
        assertTrue(recipe.indexOf("PigmeeConversionLogic.createResult(target)")
                < recipe.indexOf("target.copyWithCount(OUTPUT_COUNT)"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
