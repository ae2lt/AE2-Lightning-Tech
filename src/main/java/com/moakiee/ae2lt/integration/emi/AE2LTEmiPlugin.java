package com.moakiee.ae2lt.integration.emi;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockStructureRecipes;
import com.moakiee.ae2lt.registry.ModBlocks;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Native EMI registration for recipe-viewer features that cannot be bridged from JEI. */
@EmiEntrypoint
public final class AE2LTEmiPlugin implements EmiPlugin {
    public static final EmiRecipeCategory MULTIBLOCK_STRUCTURE = new EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "multiblock_structure"),
            EmiStack.of(ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.get())) {
        @Override
        public Component getName() {
            return Component.translatable("jei.ae2lt.multiblock.title");
        }
    };

    @Override
    public void register(EmiRegistry registry) {
        EmiMultiblockInputEvents.register();
        registry.addCategory(MULTIBLOCK_STRUCTURE);
        registry.addWorkstation(
                MULTIBLOCK_STRUCTURE,
                EmiStack.of(ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.get()));
        registry.addWorkstation(
                MULTIBLOCK_STRUCTURE,
                EmiStack.of(ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get()));
        MultiblockStructureRecipes.all().stream()
                .map(EmiMultiblockStructureRecipe::new)
                .forEach(registry::addRecipe);
    }
}
