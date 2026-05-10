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
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
    private static final Identifier ID =
            Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "jei_plugin");

    public JEIPlugin() {
        AE2JeiIntegrationCompat.registerConverter();
    }

    @Override
    public Identifier getPluginUid() {
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
                new CrystalCatalyzerCategory(guiHelper));
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
                ClientRecipeSyncCache.getRecipes(com.moakiee.ae2lt.registry.ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get())
                        .stream()
                        .filter(holder -> !holder.value().getOutputTemplate().isEmpty())
                        .toList());
        registration.addRecipes(
                LightningAssemblyCategory.TYPE,
                ClientRecipeSyncCache.getRecipes(
                        com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE.get()));
        registration.addRecipes(
                LightningSimulationCategory.TYPE,
                ClientRecipeSyncCache.getRecipes(
                        com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get()));
        registration.addRecipes(
                LightningTransformCategory.TYPE,
                ClientRecipeSyncCache.getRecipes(
                        com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_TRANSFORM_TYPE.get()));
        registration.addRecipes(
                LightningStrikeCategory.TYPE,
                ClientRecipeSyncCache.getRecipes(
                        com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_STRIKE_TYPE.get()));
        registration.addRecipes(
                OverloadProcessingCategory.TYPE,
                ClientRecipeSyncCache.getRecipes(
                        com.moakiee.ae2lt.registry.ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(LightningAssemblyCategory.TYPE, ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.toStack());
        registration.addCraftingStation(LightningSimulationCategory.TYPE, ModBlocks.LIGHTNING_SIMULATION_CHAMBER.toStack());
        registration.addCraftingStation(OverloadProcessingCategory.TYPE, ModBlocks.OVERLOAD_PROCESSING_FACTORY.toStack());
        registration.addCraftingStation(TeslaCoilCategory.TYPE, ModBlocks.TESLA_COIL.toStack());
        registration.addCraftingStation(CrystalCatalyzerCategory.TYPE, ModBlocks.CRYSTAL_CATALYZER.toStack());
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
            int x, int y, int width, int height, IRecipeType<?> recipeType) {
        return new IGuiContainerHandler<T>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(T screen, double mouseX, double mouseY) {
                return List.of(IGuiClickableArea.createBasic(x, y, width, height, recipeType));
            }
        };
    }
}
