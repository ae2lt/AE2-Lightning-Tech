package com.moakiee.ae2lt.integration.emi;

import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

final class EmiOverloadProcessingRecipe extends EmiBackedRecipe<OverloadProcessingRecipe> {
    private static final ResourceLocation TEXTURE =
            EmiRecipeWidgets.texture("guis/overload_processing_factory.png");
    private static final int WIDTH = 168;
    private static final int FLUID_HEIGHT = 54;
    private static final int FIRST_TICK_PIXELS = 5;

    private final EmiStack fluidInput;
    private final EmiStack fluidOutput;

    EmiOverloadProcessingRecipe(RecipeHolder<OverloadProcessingRecipe> holder) {
        super(AE2LTEmiCategories.OVERLOAD_PROCESSING, holder, WIDTH, 90);
        recipe.itemInputs().forEach(input -> inputs.add(EmiRecipeWidgets.ingredient(input.ingredient(), input.count())));
        fluidInput = recipe.fluidInput().isEmpty()
                ? EmiStack.EMPTY
                : EmiRecipeWidgets.fluid(recipe.fluidInput());
        if (!fluidInput.isEmpty()) {
            inputs.add(fluidInput);
        }
        recipe.itemResults().forEach(result -> outputs.add(EmiStack.of(result)));
        fluidOutput = recipe.fluidResult().isEmpty()
                ? EmiStack.EMPTY
                : EmiRecipeWidgets.fluid(recipe.fluidResult());
        if (!fluidOutput.isEmpty()) {
            outputs.add(fluidOutput);
        }
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addTexture(TEXTURE, 0, 0, WIDTH, 68, 4, 14);

        for (int index = 0; index < recipe.itemInputs().size(); index++) {
            EmiRecipeWidgets.addLargeStackSlot(
                            widgets,
                            inputs.get(index),
                            25 + index % 3 * 18,
                            10 + index / 3 * 18)
                    .drawBack(false);
        }
        if (!fluidInput.isEmpty()) {
            widgets.addTank(
                            fluidInput,
                            5,
                            9,
                            16,
                            FLUID_HEIGHT,
                            displayCapacity(
                                    recipe.fluidInput().getAmount(),
                                    OverloadProcessingFactoryBlockEntity.INPUT_TANK_CAPACITY))
                    .drawBack(false);
        }
        if (!recipe.itemResults().isEmpty()) {
            EmiRecipeWidgets.addLargeStackSlot(
                            widgets,
                            EmiStack.of(recipe.itemResults().get(0)),
                            114,
                            28)
                    .drawBack(false)
                    .recipeContext(this);
        }
        if (!fluidOutput.isEmpty()) {
            widgets.addTank(
                            fluidOutput,
                            147,
                            9,
                            16,
                            FLUID_HEIGHT,
                            displayCapacity(
                                    recipe.fluidResult().getAmount(),
                                    OverloadProcessingFactoryBlockEntity.OUTPUT_TANK_CAPACITY))
                    .drawBack(false)
                    .recipeContext(this);
        }

        widgets.addAnimatedTexture(
                TEXTURE,
                80,
                32,
                31,
                10,
                176,
                18,
                2_000,
                true,
                false,
                false);
        EmiRecipeWidgets.centeredText(
                widgets,
                Component.translatable(
                        "jei.ae2lt.overload_processing.energy",
                        EmiRecipeWidgets.compactEnergy(recipe.totalEnergy())),
                WIDTH / 2,
                70);
        EmiRecipeWidgets.centeredText(
                widgets,
                Component.translatable(
                        "jei.ae2lt.overload_processing.lightning",
                        recipe.lightningCost(),
                        EmiRecipeWidgets.tierName(recipe.lightningTier())),
                WIDTH / 2,
                80);
    }

    private static int displayCapacity(int amount, int tankCapacity) {
        if (amount <= 0) {
            return Math.max(1, tankCapacity);
        }
        long tickFloorCapacity = Math.max(1L, (long) amount * FLUID_HEIGHT / FIRST_TICK_PIXELS);
        return (int) Math.min(tankCapacity, tickFloorCapacity);
    }
}
