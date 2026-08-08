package com.moakiee.ae2lt.integration.emi;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.util.RecipeManagerByTypeAccess;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiRenderable;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Native EMI categories mirroring the dedicated JEI category layouts. */
final class AE2LTEmiCategories {
    static final EmiRecipeCategory OVERLOAD_GROWTH = category(
            "overload_growth", EmiStack.of(ModBlocks.OVERLOAD_CRYSTAL_CLUSTER.get()));
    static final EmiRecipeCategory LIGHTNING_ASSEMBLY = category(
            "lightning_assembly", EmiStack.of(ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get()));
    static final EmiRecipeCategory LIGHTNING_SIMULATION = category(
            "lightning_simulation", EmiStack.of(ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get()));
    static final EmiRecipeCategory LIGHTNING_TRANSFORM = category(
            "lightning_transform", new EmiLightningIcon(false));
    static final EmiRecipeCategory LIGHTNING_STRIKE = category(
            "lightning_strike", new EmiLightningIcon(false));
    static final EmiRecipeCategory OVERLOAD_PROCESSING = category(
            "overload_processing", EmiStack.of(ModBlocks.OVERLOAD_PROCESSING_FACTORY.get()));
    static final EmiRecipeCategory TESLA_COIL = category(
            "tesla_coil", EmiStack.of(ModBlocks.TESLA_COIL.get()));
    static final EmiRecipeCategory CRYSTAL_CATALYZER = category(
            "crystal_catalyzer", EmiStack.of(ModBlocks.CRYSTAL_CATALYZER.get()));
    static final EmiRecipeCategory FIRMAMENT_CONVERSION = category(
            "firmament_conversion", EmiStack.of(ModBlocks.FIRMAMENT_CONVERSION_CORE.get()));

    private AE2LTEmiCategories() {
    }

    static void register(EmiRegistry registry) {
        addCategory(registry, OVERLOAD_GROWTH);
        addCategory(registry, LIGHTNING_TRANSFORM);
        addCategory(registry, LIGHTNING_STRIKE);
        addCategory(registry, LIGHTNING_ASSEMBLY, new ItemStack(ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get()));
        addCategory(registry, LIGHTNING_SIMULATION, new ItemStack(ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get()));
        addCategory(registry, OVERLOAD_PROCESSING, new ItemStack(ModBlocks.OVERLOAD_PROCESSING_FACTORY.get()));
        addCategory(registry, TESLA_COIL, new ItemStack(ModBlocks.TESLA_COIL.get()));
        addCategory(registry, CRYSTAL_CATALYZER, new ItemStack(ModBlocks.CRYSTAL_CATALYZER.get()));
        addCategory(registry, FIRMAMENT_CONVERSION, new ItemStack(ModBlocks.FIRMAMENT_CONVERSION_CORE.get()));

        EmiOverloadGrowthRecipe.registerAll(registry);
        EmiTeslaCoilRecipe.registerAll(registry);
        // 1.20.1: getAllRecipesFor strips recipe ids; the byType bridge keeps the
        // id->recipe map so EMI display ids still match the datapack.
        var recipeManager = registry.getRecipeManager();
        RecipeManagerByTypeAccess.byType(recipeManager, ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE.get())
                .forEach((id, recipe) -> registry.addRecipe(new EmiLightningAssemblyRecipe(id, recipe)));
        RecipeManagerByTypeAccess.byType(recipeManager, ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get())
                .forEach((id, recipe) -> registry.addRecipe(new EmiLightningSimulationRecipe(id, recipe)));
        RecipeManagerByTypeAccess.byType(recipeManager, ModRecipeTypes.LIGHTNING_TRANSFORM_TYPE.get())
                .forEach((id, recipe) -> registry.addRecipe(new EmiLightningTransformRecipe(id, recipe)));
        RecipeManagerByTypeAccess.byType(recipeManager, ModRecipeTypes.LIGHTNING_STRIKE_TYPE.get())
                .forEach((id, recipe) -> registry.addRecipe(new EmiLightningStrikeRecipe(id, recipe)));
        RecipeManagerByTypeAccess.byType(recipeManager, ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get())
                .forEach((id, recipe) -> registry.addRecipe(new EmiOverloadProcessingRecipe(id, recipe)));
        RecipeManagerByTypeAccess.byType(recipeManager, ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get())
                .forEach((id, recipe) -> {
                    if (!recipe.getOutputTemplate().isEmpty()) {
                        registry.addRecipe(new EmiCrystalCatalyzerRecipe(id, recipe));
                    }
                });
        RecipeManagerByTypeAccess.byType(recipeManager, ModRecipeTypes.FIRMAMENT_CONVERSION_TYPE.get())
                .forEach((id, recipe) -> registry.addRecipe(new EmiFirmamentConversionRecipe(id, recipe)));
    }

    private static EmiRecipeCategory category(String path, EmiRenderable icon) {
        return new EmiRecipeCategory(
                ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, path), icon) {
            @Override
            public Component getName() {
                return Component.translatable("jei.ae2lt." + path + ".title");
            }
        };
    }

    private static void addCategory(
            EmiRegistry registry, EmiRecipeCategory category, ItemStack... workstations) {
        registry.addCategory(category);
        for (ItemStack workstation : workstations) {
            registry.addWorkstation(category, EmiStack.of(workstation));
        }
    }
}
