package com.moakiee.ae2lt.integration.emi;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.integration.recipeviewer.multiblock.InteractiveMultiblockPreview;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockStructureRecipe;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

/** EMI recipe page backed by the same immutable structure description as JEI. */
final class EmiMultiblockStructureRecipe implements EmiRecipe {
    private final MultiblockStructureRecipe structure;
    private final List<EmiIngredient> inputs;
    private final List<EmiStack> outputs;

    EmiMultiblockStructureRecipe(MultiblockStructureRecipe structure) {
        this.structure = structure;
        this.inputs = structure.materials().stream()
                .map(material -> (EmiIngredient) EmiStack.of(material.block(), material.count()))
                .toList();
        this.outputs = structure.focusStacks().stream()
                .map(EmiStack::of)
                .toList();
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return AE2LTEmiPlugin.MULTIBLOCK_STRUCTURE;
    }

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath(structure.id().getNamespace(),
                "/" + structure.id().getPath());
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        return InteractiveMultiblockPreview.DEFAULT_WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return InteractiveMultiblockPreview.DEFAULT_HEIGHT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.add(new EmiInteractiveMultiblockWidget(
                structure,
                widgets.getWidth(),
                widgets.getHeight()));
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public boolean hideCraftable() {
        return true;
    }

    @Override
    public @Nullable Recipe<?> getBackingRecipe() {
        return null;
    }
}
