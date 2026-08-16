package com.moakiee.ae2lt.integration.emi;

import com.moakiee.ae2lt.lightning.LightningTransformRecipe;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionRecipe;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipeService;
import com.moakiee.ae2lt.me.key.LightningKey;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

abstract class EmiBackedRecipe<T extends Recipe<?>> extends BasicEmiRecipe {
    protected final RecipeHolder<T> holder;
    protected final T recipe;

    EmiBackedRecipe(
            dev.emi.emi.api.recipe.EmiRecipeCategory category,
            RecipeHolder<T> holder,
            int width,
            int height) {
        super(category, holder.id(), width, height);
        this.holder = holder;
        this.recipe = holder.value();
    }

    @Override
    public RecipeHolder<?> getBackingRecipe() {
        return holder;
    }
}

final class EmiLightningAssemblyRecipe extends EmiBackedRecipe<LightningAssemblyRecipe> {
    private static final ResourceLocation TEXTURE =
            EmiRecipeWidgets.texture("guis/lightning_assembly_chamber.png");
    private static final int WIDTH = 156;
    private static final int BACKGROUND_HEIGHT = 78;

    EmiLightningAssemblyRecipe(RecipeHolder<LightningAssemblyRecipe> holder) {
        super(AE2LTEmiCategories.LIGHTNING_ASSEMBLY, holder, WIDTH, 100);
        recipe.inputs().forEach(input -> inputs.add(EmiRecipeWidgets.ingredient(input.ingredient(), input.count())));
        outputs.add(EmiStack.of(recipe.getResultStack()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addTexture(TEXTURE, 0, 0, WIDTH, BACKGROUND_HEIGHT, 19, 10);
        for (int index = 0; index < inputs.size() && index < 9; index++) {
            int x = 10 + index % 3 * 18;
            int y = 21 + index / 3 * 18;
            EmiRecipeWidgets.addLargeStackSlot(widgets, inputs.get(index), x, y).drawBack(false);
        }
        EmiRecipeWidgets.addLargeStackSlot(widgets, outputs.getFirst(), 107, 39)
                .drawBack(false)
                .recipeContext(this);
        EmiRecipeWidgets.centeredText(
                widgets,
                Component.translatable(
                        "jei.ae2lt.lightning_assembly.energy",
                        EmiRecipeWidgets.compactEnergy(recipe.totalEnergy())),
                WIDTH / 2,
                80);
        EmiRecipeWidgets.centeredText(
                widgets,
                Component.translatable(
                        "jei.ae2lt.lightning_assembly.lightning",
                        recipe.lightningCost(),
                        EmiRecipeWidgets.tierName(recipe.lightningTier())),
                WIDTH / 2,
                90);
    }
}

final class EmiLightningSimulationRecipe extends EmiBackedRecipe<LightningSimulationRecipe> {
    private static final ResourceLocation TEXTURE =
            EmiRecipeWidgets.texture("guis/lightning_simulation_room.png");
    private static final int WIDTH = 168;

    EmiLightningSimulationRecipe(RecipeHolder<LightningSimulationRecipe> holder) {
        super(AE2LTEmiCategories.LIGHTNING_SIMULATION, holder, WIDTH, 96);
        recipe.inputs().forEach(input -> inputs.add(EmiRecipeWidgets.ingredient(input.ingredient(), input.count())));
        outputs.add(EmiStack.of(recipe.getResultStack()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addTexture(TEXTURE, 0, 0, WIDTH, 64, 5, 14);
        for (int index = 0; index < inputs.size(); index++) {
            EmiRecipeWidgets.addLargeStackSlot(widgets, inputs.get(index), 34, 8 + index * 18)
                    .drawBack(false);
        }
        EmiRecipeWidgets.addLargeStackSlot(widgets, outputs.getFirst(), 114, 27)
                .drawBack(false)
                .recipeContext(this);
        EmiRecipeWidgets.centeredText(
                widgets,
                Component.translatable(
                        "jei.ae2lt.lightning_simulation.energy",
                        EmiRecipeWidgets.compactEnergy(recipe.totalEnergy())),
                WIDTH / 2,
                66);
        EmiRecipeWidgets.centeredText(
                widgets,
                Component.translatable(
                        "jei.ae2lt.lightning_simulation.lightning",
                        recipe.lightningCost(),
                        EmiRecipeWidgets.tierName(recipe.lightningTier())),
                WIDTH / 2,
                76);
        if (recipe.lightningTier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE) {
            EmiRecipeWidgets.centeredText(
                    widgets,
                    Component.translatable(
                            "jei.ae2lt.lightning_simulation.substitution",
                            LightningSimulationRecipeService.getEquivalentHighVoltageCost(
                                    recipe.lightningTier(), recipe.lightningCost())),
                    WIDTH / 2,
                    86);
        }
    }
}

final class EmiLightningTransformRecipe extends EmiBackedRecipe<LightningTransformRecipe> {
    private static final int WIDTH = 134;
    private static final EmiLightningIcon LIGHTNING = new EmiLightningIcon(false);

    EmiLightningTransformRecipe(RecipeHolder<LightningTransformRecipe> holder) {
        super(AE2LTEmiCategories.LIGHTNING_TRANSFORM, holder, WIDTH, 66);
        recipe.inputs().forEach(input -> inputs.add(EmiRecipeWidgets.ingredient(input.ingredient(), input.count())));
        outputs.add(EmiStack.of(recipe.getResultItem(Minecraft.getInstance().level.registryAccess())));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        int x = 5;
        int y = 5;
        if (inputs.size() < 3) {
            y += (3 - inputs.size()) * 10;
        }
        for (var input : inputs) {
            EmiRecipeWidgets.addLargeStackSlot(widgets, input, x + 1, y + 1);
            y += 20;
            if (y >= 65) {
                y -= 60;
                x += 18;
            }
        }
        widgets.addFillingArrow(28, 24, 2_000);
        widgets.addFillingArrow(81, 24, 2_000);
        widgets.addDrawable(57, 26, 16, 16, (graphics, mouseX, mouseY, delta) ->
                LIGHTNING.render(graphics, 0, 0, delta));
        EmiRecipeWidgets.addLargeStackSlot(widgets, outputs.getFirst(), 111, 26)
                .recipeContext(this);
        EmiRecipeWidgets.centeredText(
                widgets,
                Component.translatable("jei.ae2lt.lightning_transform.label"),
                WIDTH / 2,
                4);
    }
}

final class EmiFirmamentConversionRecipe extends EmiBackedRecipe<FirmamentConversionRecipe> {
    private static final int WIDTH = 134;
    private static final int SLOT_AREA_Y = 4;
    private static final int SLOT_AREA_HEIGHT = 54;

    EmiFirmamentConversionRecipe(RecipeHolder<FirmamentConversionRecipe> holder) {
        super(AE2LTEmiCategories.FIRMAMENT_CONVERSION, holder, WIDTH, 76);
        recipe.inputs().forEach(input -> inputs.add(EmiRecipeWidgets.ingredient(input.ingredient(), input.count())));
        recipe.getResultStacks().forEach(result -> outputs.add(EmiStack.of(result)));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        int inputOffsetY = (SLOT_AREA_HEIGHT - inputs.size() * 18) / 2;
        for (int index = 0; index < inputs.size(); index++) {
            EmiRecipeWidgets.addLargeStackSlot(
                    widgets,
                    inputs.get(index),
                    19,
                    SLOT_AREA_Y + inputOffsetY + index * 18 + 1);
        }

        int outputCols = Math.min(outputs.size(), 2);
        int outputRows = (outputs.size() + 1) / 2;
        int outputOffsetY = (SLOT_AREA_HEIGHT - outputRows * 18) / 2;
        int outputOffsetX = (36 - outputCols * 18) / 2;
        for (int index = 0; index < outputs.size(); index++) {
            int col = index % 2;
            int row = index / 2;
            EmiRecipeWidgets.addLargeStackSlot(
                            widgets,
                            outputs.get(index),
                            81 + outputOffsetX + col * 18,
                            SLOT_AREA_Y + outputOffsetY + row * 18 + 1)
                    .recipeContext(this);
        }
        widgets.addFillingArrow(46, 22, 2_000);
        EmiRecipeWidgets.centeredText(
                widgets,
                Component.translatable(
                        "jei.ae2lt.firmament_conversion.time",
                        EmiRecipeWidgets.processTime(recipe.processTime())),
                WIDTH / 2,
                64);
    }
}
