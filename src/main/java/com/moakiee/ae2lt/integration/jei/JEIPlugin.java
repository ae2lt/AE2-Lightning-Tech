package com.moakiee.ae2lt.integration.jei;

import java.util.Collection;
import java.util.List;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWirelessTerminalFactory;
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
import com.moakiee.ae2lt.integration.jei.category.MultiblockStructureCategory;
import com.moakiee.ae2lt.integration.jei.category.OverloadGrowthCategory;
import com.moakiee.ae2lt.integration.jei.category.OverloadProcessingCategory;
import com.moakiee.ae2lt.integration.jei.category.TeslaCoilCategory;
import com.moakiee.ae2lt.integration.jei.compat.ae2jeiintegration.AE2JeiIntegrationCompat;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockStructureRecipes;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.util.RecipeManagerByTypeAccess;
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
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
    private static final ResourceLocation ID =
            new ResourceLocation(AE2LightningTech.MODID, "jei_plugin");
    private static final String EMI_MODID = "emi";

    public JEIPlugin() {
        // 1.20.1: AE2's IngredientConverters extension point lives in the AE2 main jar;
        // the 1.21 standalone AE2JEIIntegration mod does not exist for 1.20.1, so this
        // plugin class is itself proof that JEI is loaded.
        AE2JeiIntegrationCompat.registerConverter();
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        // 1.20.1 JEI has no ingredient codec parameter (added in 1.21).
        registration.register(
                LightningJeiIngredients.TYPE,
                LightningJeiIngredients.INGREDIENTS,
                LightningJeiIngredients.HELPER,
                LightningJeiIngredients.RENDERER);
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
        // EMI has a native adapter for this highly interactive page. Avoid also feeding
        // the JEI version through EMI's JEI bridge when both viewers are installed.
        if (!isEmiLoaded()) {
            registration.addRecipeCategories(new MultiblockStructureCategory(guiHelper));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(OverloadGrowthCategory.TYPE, List.of(OverloadGrowthCategory.Page.values()));
        registration.addRecipes(TeslaCoilCategory.TYPE, List.of(TeslaCoilCategory.Page.values()));
        if (!isEmiLoaded()) {
            registration.addRecipes(MultiblockStructureCategory.TYPE, MultiblockStructureRecipes.all());
        }
        registration.addIngredientInfo(
                ModItems.PIGMEE_CORE.get(),
                Component.translatable("jei.ae2lt.pigmee_core.info"));

        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        // 1.20.1: getAllRecipesFor drops recipe ids, so the byType bridge is used
        // to keep the id->recipe map that JEI categories resolve by id.
        registration.addRecipes(
                CrystalCatalyzerCategory.TYPE,
                RecipeManagerByTypeAccess.byType(
                                level.getRecipeManager(),
                                com.moakiee.ae2lt.registry.ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get())
                        .values()
                        .stream()
                        .filter(recipe -> !recipe.getOutputTemplate().isEmpty())
                        .toList());
        registration.addRecipes(
                LightningAssemblyCategory.TYPE,
                RecipeManagerByTypeAccess.byType(
                                level.getRecipeManager(),
                                com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE.get())
                        .values()
                        .stream()
                        .toList());
        registration.addRecipes(
                LightningSimulationCategory.TYPE,
                RecipeManagerByTypeAccess.byType(
                                level.getRecipeManager(),
                                com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get())
                        .values()
                        .stream()
                        .toList());
        registration.addRecipes(
                LightningTransformCategory.TYPE,
                RecipeManagerByTypeAccess.byType(
                                level.getRecipeManager(),
                                com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_TRANSFORM_TYPE.get())
                        .values()
                        .stream()
                        .toList());
        registration.addRecipes(
                LightningStrikeCategory.TYPE,
                RecipeManagerByTypeAccess.byType(
                                level.getRecipeManager(),
                                com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_STRIKE_TYPE.get())
                        .values()
                        .stream()
                        .toList());
        registration.addRecipes(
                OverloadProcessingCategory.TYPE,
                RecipeManagerByTypeAccess.byType(
                                level.getRecipeManager(),
                                com.moakiee.ae2lt.registry.ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get())
                        .values()
                        .stream()
                        .toList());
        registration.addRecipes(
                FirmamentConversionCategory.TYPE,
                RecipeManagerByTypeAccess.byType(
                                level.getRecipeManager(),
                                com.moakiee.ae2lt.registry.ModRecipeTypes.FIRMAMENT_CONVERSION_TYPE.get())
                        .values()
                        .stream()
                        .toList());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get()), LightningAssemblyCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get()), LightningSimulationCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.OVERLOAD_PROCESSING_FACTORY.get()), OverloadProcessingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.TESLA_COIL.get()), TeslaCoilCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.CRYSTAL_CATALYZER.get()), CrystalCatalyzerCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.FIRMAMENT_CONVERSION_CORE.get()), FirmamentConversionCategory.TYPE);
        if (!isEmiLoaded()) {
            registration.addRecipeCatalyst(
                    new ItemStack(ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.get()),
                    MultiblockStructureCategory.TYPE);
            registration.addRecipeCatalyst(
                    new ItemStack(ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get()),
                    MultiblockStructureCategory.TYPE);
        }
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // AE2 1.20.1 ships its own EncodePatternTransferHandler for the vanilla
        // JEI plugin; its constructor takes (MenuType, Class, transfer-helper).
        var helper = registration.getTransferHelper();
        registration.addUniversalRecipeTransferHandler(new UniversalEncodePatternTransferHandler<>(
                TianshuPatternEncodingTermMenu.TYPE,
                TianshuPatternEncodingTermMenu.class,
                helper));
        if (TianshuWirelessTerminalFactory.isAvailable()) {
            registration.addUniversalRecipeTransferHandler(new UniversalEncodePatternTransferHandler<>(
                    TianshuWirelessPatternEncodingTermMenu.TYPE,
                    TianshuWirelessPatternEncodingTermMenu.class,
                    helper));
        }

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

    private static boolean isEmiLoaded() {
        return ModList.get().isLoaded(EMI_MODID);
    }
}
