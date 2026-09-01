package com.moakiee.ae2lt.integration.emi;

import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

final class EmiCrystalCatalyzerRecipe extends EmiBackedRecipe<CrystalCatalyzerRecipe> {
    private static final ResourceLocation TEXTURE =
            EmiRecipeWidgets.texture("guis/crystal_catalyzer.png");
    private static final int WIDTH = 128;

    private final EmiStack fluid;

    EmiCrystalCatalyzerRecipe(RecipeHolder<CrystalCatalyzerRecipe> holder) {
        super(AE2LTEmiCategories.CRYSTAL_CATALYZER, holder, WIDTH, 114);
        fluid = EmiRecipeWidgets.fluid(CrystalCatalyzerBlockEntity.getFixedFluidPerCycle());
        inputs.add(fluid);
        recipe.catalyst().ifPresent(catalyst ->
                inputs.add(EmiRecipeWidgets.ingredient(catalyst, recipe.catalystCount())));
        outputs.add(EmiStack.of(recipe.getOutputTemplate()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addTexture(TEXTURE, 0, 0, WIDTH, 62, 22, 14);
        var fixedFluid = CrystalCatalyzerBlockEntity.getFixedFluidPerCycle();
        widgets.addTank(fluid, 4, 4, 16, 53, Math.max(1, fixedFluid.getAmount()))
                .drawBack(false)
                .appendTooltip(Component.translatable(
                        "jei.ae2lt.crystal_catalyzer.fluid_fixed",
                        fixedFluid.getAmount()));

        if (inputs.size() > 1) {
            int perInstance = Math.max(1, recipe.catalystCount());
            EmiRecipeWidgets.addLargeStackSlot(widgets, inputs.get(1), 34, 16)
                    .drawBack(false)
                    .appendTooltip(Component.translatable(recipe.pigmee()
                            ? "jei.ae2lt.crystal_catalyzer.catalyst_pigmee"
                            : "jei.ae2lt.crystal_catalyzer.catalyst_parallel",
                            perInstance,
                            recipe.pigmee()
                                    ? CrystalCatalyzerInventory.PIGMEE_CATALYST_SLOT_LIMIT
                                    : CrystalCatalyzerInventory.CATALYST_SLOT_LIMIT));
        }

        int baseCount = recipe.getOutputTemplate().getCount();
        int matrixMultiplier = CrystalCatalyzerBlockEntity.MATRIX_OUTPUT_MULTIPLIER;
        int catalystPerInstance = Math.max(1, recipe.catalystCount());
        EmiRecipeWidgets.addLargeStackSlot(widgets, outputs.getFirst(), 95, 16)
                .drawBack(false)
                .recipeContext(this)
                .appendTooltip(recipe.pigmee()
                        ? Component.translatable("jei.ae2lt.crystal_catalyzer.output_pigmee", baseCount)
                        : Component.translatable(
                                "jei.ae2lt.crystal_catalyzer.output_base", baseCount, matrixMultiplier))
                .appendTooltip(recipe.pigmee()
                        ? Component.translatable("jei.ae2lt.crystal_catalyzer.pigmee_note_line2")
                        : Component.translatable(
                                "jei.ae2lt.crystal_catalyzer.output_parallel",
                                catalystPerInstance));

        widgets.addAnimatedTexture(
                TEXTURE,
                52,
                19,
                35,
                10,
                176,
                18,
                recipe.pigmee() ? 15_000 : recipe.mode() == Mode.CRYSTAL ? 1_000 : 2_000,
                true,
                false,
                false);

        // EMI may clamp a recipe to the available screen height. Keep the five JEI
        // status lines together when that happens instead of letting the last lines
        // spill below the recipe panel.
        boolean compactText = widgets.getHeight() < 114;
        int firstLineY = compactText ? 60 : 64;
        int lineSpacing = compactText ? 8 : 10;

        statusText(
                widgets,
                Component.translatable(
                        "jei.ae2lt.crystal_catalyzer.energy",
                        EmiRecipeWidgets.compactEnergy(recipe.energyPerCycle())),
                firstLineY,
                compactText);
        statusText(
                widgets,
                Component.translatable(
                        "jei.ae2lt.crystal_catalyzer.time",
                        recipe.pigmee() ? "15s" : recipe.mode() == Mode.CRYSTAL ? "1s" : "2s"),
                firstLineY + lineSpacing,
                compactText);
        statusText(
                widgets,
                recipe.pigmee()
                        ? Component.translatable("jei.ae2lt.crystal_catalyzer.lightning_none")
                        : Component.translatable(
                                "jei.ae2lt.crystal_catalyzer.lightning",
                                recipe.lightningCost(),
                                EmiRecipeWidgets.tierName(recipe.lightningTier())),
                firstLineY + lineSpacing * 2,
                compactText);
        statusText(
                widgets,
                Component.translatable(recipe.pigmee()
                        ? "jei.ae2lt.crystal_catalyzer.pigmee_note_line1"
                        : "jei.ae2lt.crystal_catalyzer.matrix_note_line1"),
                firstLineY + lineSpacing * 3,
                compactText);
        statusText(
                widgets,
                recipe.pigmee()
                        ? Component.translatable("jei.ae2lt.crystal_catalyzer.pigmee_note_line2")
                        : Component.translatable(
                                "jei.ae2lt.crystal_catalyzer.matrix_note_line2", matrixMultiplier),
                firstLineY + lineSpacing * 4,
                compactText);
    }

    private static void statusText(WidgetHolder widgets, Component text, int y, boolean compact) {
        if (!compact) {
            EmiRecipeWidgets.centeredText(widgets, text, WIDTH / 2, y);
            return;
        }

        widgets.addDrawable(0, y, WIDTH, 8, (graphics, mouseX, mouseY, delta) -> {
            var font = Minecraft.getInstance().font;
            float scale = 0.8f;
            graphics.pose().pushPose();
            graphics.pose().scale(scale, scale, 1);
            int x = Math.round((WIDTH / 2f) / scale - font.width(text) / 2f);
            graphics.drawString(font, text, x, 0, EmiRecipeWidgets.TEXT_COLOR, false);
            graphics.pose().popPose();
        });
    }
}
