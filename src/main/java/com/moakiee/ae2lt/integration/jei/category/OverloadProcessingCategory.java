package com.moakiee.ae2lt.integration.jei.category;

import java.util.Arrays;
import java.util.List;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.integration.jei.LargeStackJeiItemRenderer;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

public class OverloadProcessingCategory implements IRecipeCategory<OverloadProcessingRecipe> {
    public static final RecipeType<OverloadProcessingRecipe> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "overload_processing", OverloadProcessingRecipe.class);

    private static final ResourceLocation BACKGROUND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/guis/overload_processing_factory.png");

    private static final int BACKGROUND_U = 4;
    private static final int BACKGROUND_V = 14;
    private static final int BACKGROUND_WIDTH = 168;
    private static final int BACKGROUND_HEIGHT = 68;
    private static final int WIDTH = 168;
    private static final int HEIGHT = 90;
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    private static final int INPUT_START_X = 25;
    private static final int INPUT_START_Y = 10;
    private static final int SLOT_SPACING = 18;

    private static final int OUTPUT_X = 114;
    private static final int OUTPUT_Y = 28;

    private static final int FLUID_INPUT_X = 5;
    private static final int FLUID_INPUT_Y = 9;
    private static final int FLUID_OUTPUT_X = 147;
    private static final int FLUID_OUTPUT_Y = 9;
    private static final int FLUID_WIDTH = 16;
    private static final int FLUID_HEIGHT = 54;
    private static final int FIRST_TICK_PIXELS = 5;

    private static final int PROCESS_X = 80;
    private static final int PROCESS_Y = 32;
    private static final int PROCESS_OVERLAY_U = 176;
    private static final int PROCESS_OVERLAY_V = 18;
    private static final int PROCESS_OVERLAY_WIDTH = 31;
    private static final int PROCESS_OVERLAY_HEIGHT = 10;
    private static final long PROCESS_CYCLE_MS = 2_000L;

    private static final int ENERGY_TEXT_Y = 70;
    private static final int LIGHTNING_TEXT_Y = 80;

    private final IDrawable icon;
    private final IDrawable background;

    public OverloadProcessingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.OVERLOAD_PROCESSING_FACTORY.get()));
        this.background = guiHelper.createDrawable(
                BACKGROUND_TEXTURE, BACKGROUND_U, BACKGROUND_V, BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
    }

    @Override
    public RecipeType<OverloadProcessingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ae2lt.overload_processing.title");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, OverloadProcessingRecipe recipe, IFocusGroup focuses) {
        for (int index = 0; index < recipe.itemInputs().size(); index++) {
            var input = recipe.itemInputs().get(index);
            int col = index % 3;
            int row = index / 3;
            builder.addSlot(
                            RecipeIngredientRole.INPUT,
                            INPUT_START_X + col * SLOT_SPACING,
                            INPUT_START_Y + row * SLOT_SPACING)
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStacks(expandIngredient(input.ingredient(), input.count()))
                    .addRichTooltipCallback((recipeSlotView, tooltip) ->
                            LargeStackCountRenderer.appendCountTooltip(tooltip, input.count()));
        }

        if (!recipe.fluidInput().isEmpty()) {
            var fluidInput = recipe.fluidInput();
            builder.addSlot(RecipeIngredientRole.INPUT, FLUID_INPUT_X, FLUID_INPUT_Y)
                    .setFluidRenderer(
                            displayCapacity(fluidInput.getAmount(),
                                    OverloadProcessingFactoryBlockEntity.INPUT_TANK_CAPACITY),
                            false,
                            FLUID_WIDTH,
                            FLUID_HEIGHT)
                    .addIngredient(NeoForgeTypes.FLUID_STACK, fluidInput);
        }

        if (!recipe.itemResults().isEmpty()) {
            var result = recipe.itemResults().getFirst();
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStack(result)
                    .addRichTooltipCallback((recipeSlotView, tooltip) ->
                            LargeStackCountRenderer.appendCountTooltip(tooltip, result.getCount()));
        }

        if (!recipe.fluidResult().isEmpty()) {
            var fluidResult = recipe.fluidResult();
            builder.addSlot(RecipeIngredientRole.OUTPUT, FLUID_OUTPUT_X, FLUID_OUTPUT_Y)
                    .setFluidRenderer(
                            displayCapacity(fluidResult.getAmount(),
                                    OverloadProcessingFactoryBlockEntity.OUTPUT_TANK_CAPACITY),
                            false,
                            FLUID_WIDTH,
                            FLUID_HEIGHT)
                    .addIngredient(NeoForgeTypes.FLUID_STACK, fluidResult);
        }
    }

    @Override
    public void draw(
            OverloadProcessingRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        background.draw(guiGraphics);
        drawProcessOverlay(guiGraphics);

        var font = Minecraft.getInstance().font;
        var energyText = Component.translatable(
                "jei.ae2lt.overload_processing.energy",
                formatCompactEnergy(recipe.totalEnergy()));
        int energyX = (WIDTH - font.width(energyText)) / 2;
        guiGraphics.drawString(font, energyText, energyX, ENERGY_TEXT_Y, 0x404040, false);

        var lightningText = Component.translatable(
                "jei.ae2lt.overload_processing.lightning",
                recipe.lightningCost(),
                Component.translatable(recipe.lightningTier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                        ? "ae2lt.gui.lightning_simulation.tier.extreme_high_voltage"
                        : "ae2lt.gui.lightning_simulation.tier.high_voltage"));
        int lightningX = (WIDTH - font.width(lightningText)) / 2;
        guiGraphics.drawString(font, lightningText, lightningX, LIGHTNING_TEXT_Y, 0x404040, false);
    }

    private void drawProcessOverlay(GuiGraphics guiGraphics) {
        long elapsed = Util.getMillis() % PROCESS_CYCLE_MS;
        double progress = elapsed / (double) PROCESS_CYCLE_MS;
        int width = Mth.clamp((int) Math.ceil(progress * PROCESS_OVERLAY_WIDTH), 0, PROCESS_OVERLAY_WIDTH);
        if (width <= 0) {
            return;
        }
        guiGraphics.blit(
                BACKGROUND_TEXTURE,
                PROCESS_X,
                PROCESS_Y,
                PROCESS_OVERLAY_U,
                PROCESS_OVERLAY_V,
                width,
                PROCESS_OVERLAY_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
    }

    private static List<ItemStack> expandIngredient(Ingredient ingredient, int count) {
        return Arrays.stream(ingredient.getItems())
                .map(stack -> stack.copyWithCount(count))
                .toList();
    }

    private static long displayCapacity(int amount, int tankCapacity) {
        if (amount <= 0) {
            return Math.max(1, tankCapacity);
        }
        long tickFloorCapacity = Math.max(1L, (long) amount * FLUID_HEIGHT / FIRST_TICK_PIXELS);
        return Math.min((long) tankCapacity, tickFloorCapacity);
    }

    private static String formatCompactEnergy(long energy) {
        if (energy >= 1_000_000L) {
            return formatCompactValue(energy / 1_000_000D, "m");
        }
        if (energy >= 1_000L) {
            return formatCompactValue(energy / 1_000D, "k");
        }
        return Long.toString(energy);
    }

    private static String formatCompactValue(double value, String suffix) {
        double rounded = Math.round(value * 10.0D) / 10.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001D) {
            return Long.toString(Math.round(rounded)) + suffix;
        }
        return rounded + suffix;
    }
}
