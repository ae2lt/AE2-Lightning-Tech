package com.moakiee.ae2lt.integration.jei;

import java.util.Collection;
import java.util.List;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.CrystalCatalyzerScreen;
import com.moakiee.ae2lt.client.LightningAssemblyChamberScreen;
import com.moakiee.ae2lt.client.LightningSimulationChamberScreen;
import com.moakiee.ae2lt.client.OverloadProcessingFactoryScreen;
import com.moakiee.ae2lt.client.TeslaCoilScreen;
import com.moakiee.ae2lt.integration.jei.category.CrystalCatalyzerCategory;
import com.moakiee.ae2lt.integration.jei.category.FirmamentConversionCategory;
import com.moakiee.ae2lt.integration.jei.category.LightningAssemblyCategory;
import com.moakiee.ae2lt.integration.jei.category.LightningSimulationCategory;
import com.moakiee.ae2lt.integration.jei.category.LightningStrikeCategory;
import com.moakiee.ae2lt.integration.jei.category.LightningTransformCategory;
import com.moakiee.ae2lt.integration.jei.category.OverloadGrowthCategory;
import com.moakiee.ae2lt.integration.jei.category.OverloadProcessingCategory;
import com.moakiee.ae2lt.integration.jei.category.TeslaCoilCategory;
import com.moakiee.ae2lt.integration.jei.compat.ae2jeiintegration.AE2JeiIntegrationCompat;
import com.moakiee.ae2lt.registry.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "jei_plugin");
    private static final String AE2_JEI_INTEGRATION_MODID = "ae2jeiintegration";

    public JEIPlugin() {
        if (ModList.get().isLoaded(AE2_JEI_INTEGRATION_MODID)) {
            AE2JeiIntegrationCompat.registerConverter();
        }
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        registration.register(
                LightningJeiIngredients.TYPE,
                LightningJeiIngredients.INGREDIENTS,
                LightningJeiIngredients.HELPER,
                LightningJeiIngredients.RENDERER,
                LightningJeiIngredients.CODEC);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new OverloadGrowthCategory(guiHelper),
                new LightningAssemblyCategory(guiHelper),
                new LightningSimulationCategory(guiHelper),
                new LightningTransformCategory(guiHelper),
                new LightningStrikeCategory(guiHelper),
                new OverloadProcessingCategory(guiHelper),
                new TeslaCoilCategory(guiHelper),
                new CrystalCatalyzerCategory(guiHelper),
                new FirmamentConversionCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(OverloadGrowthCategory.TYPE, List.of(OverloadGrowthCategory.Page.values()));
        registration.addRecipes(TeslaCoilCategory.TYPE, List.of(TeslaCoilCategory.Page.values()));

        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        registration.addRecipes(
                CrystalCatalyzerCategory.TYPE,
                level.getRecipeManager()
                        .getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get())
                        .stream()
                        .map(RecipeHolder::value)
                        .filter(recipe -> !recipe.getOutputTemplate().isEmpty())
                        .toList());
        registration.addRecipes(
                LightningAssemblyCategory.TYPE,
                level.getRecipeManager()
                        .getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE.get())
                        .stream()
                        .map(RecipeHolder::value)
                        .toList());
        registration.addRecipes(
                LightningSimulationCategory.TYPE,
                level.getRecipeManager()
                        .getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get())
                        .stream()
                        .map(RecipeHolder::value)
                        .toList());
        registration.addRecipes(
                LightningTransformCategory.TYPE,
                level.getRecipeManager()
                        .getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_TRANSFORM_TYPE.get())
                        .stream()
                        .map(RecipeHolder::value)
                        .toList());
        registration.addRecipes(
                LightningStrikeCategory.TYPE,
                level.getRecipeManager()
                        .getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_STRIKE_TYPE.get())
                        .stream()
                        .map(RecipeHolder::value)
                        .toList());
        registration.addRecipes(
                OverloadProcessingCategory.TYPE,
                level.getRecipeManager()
                        .getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get())
                        .stream()
                        .map(RecipeHolder::value)
                        .toList());
        registration.addRecipes(
                FirmamentConversionCategory.TYPE,
                level.getRecipeManager()
                        .getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.FIRMAMENT_CONVERSION_TYPE.get())
                        .stream()
                        .map(RecipeHolder::value)
                        .toList());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.toStack(), LightningAssemblyCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.LIGHTNING_SIMULATION_CHAMBER.toStack(), LightningSimulationCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.OVERLOAD_PROCESSING_FACTORY.toStack(), OverloadProcessingCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.TESLA_COIL.toStack(), TeslaCoilCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.CRYSTAL_CATALYZER.toStack(), CrystalCatalyzerCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.FIRMAMENT_CONVERSION_CORE.toStack(), FirmamentConversionCategory.TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(LightningAssemblyChamberScreen.class,
                clickableAreaHandler(83, 22, 42, 46, LightningAssemblyCategory.TYPE));
        registration.addGuiContainerHandler(LightningSimulationChamberScreen.class,
                clickableAreaHandler(82, 25, 35, 46, LightningSimulationCategory.TYPE));
        registration.addGuiContainerHandler(OverloadProcessingFactoryScreen.class,
                clickableAreaHandler(84, 46, 31, 10, OverloadProcessingCategory.TYPE));
        registration.addGuiContainerHandler(TeslaCoilScreen.class,
                clickableAreaHandler(43, 22, 36, 40, TeslaCoilCategory.TYPE));
        registration.addGuiContainerHandler(CrystalCatalyzerScreen.class,
                clickableAreaHandler(74, 33, 35, 10, CrystalCatalyzerCategory.TYPE));
    }

    private static <T extends AbstractContainerScreen<?>> IGuiContainerHandler<T> clickableAreaHandler(
            int x, int y, int width, int height, RecipeType<?> recipeType) {
        return new IGuiContainerHandler<T>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(T screen, double mouseX, double mouseY) {
                return List.of(IGuiClickableArea.createBasic(x, y, width, height, recipeType));
            }
        };
    }
}
