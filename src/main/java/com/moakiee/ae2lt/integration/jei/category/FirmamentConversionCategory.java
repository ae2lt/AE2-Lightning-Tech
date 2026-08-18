package com.moakiee.ae2lt.integration.jei.category;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.integration.jei.LargeStackJeiItemRenderer;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionRecipe;
import com.moakiee.ae2lt.registry.ModBlocks;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

/**
 * JEI category for the Firmament Conversion Core.
 *
 * <p>Layout (no machine GUI – the block has no screen):
 * <pre>
 *   [input 0]                   [output 0] [output 1]
 *   [input 1]  →  arrow  →     [output 2] [output 3]
 *   [input 2]
 *
 *              Processing Time: Xs
 * </pre>
 *
 * <p>Inputs are vertically centred when fewer than 3 are present; outputs
 * are arranged in a 2×2 grid that is also vertically centred.
 * All slot backgrounds use {@code setStandardSlotBackground()} so they are
 * a uniform 18×18 and do not overlap in the grid.
 */
public class FirmamentConversionCategory implements IRecipeCategory<FirmamentConversionRecipe> {
    public static final RecipeType<FirmamentConversionRecipe> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "firmament_conversion", FirmamentConversionRecipe.class);

    private static final int WIDTH = 134;
    private static final int SLOT = 18;

    // Horizontal layout (centred):
    //   input(18) + gap(10) + arrow(24) + gap(10) + output(2×18=36) = 98
    //   left margin = (134 - 98) / 2 = 18
    private static final int INPUT_X = 18;
    private static final int ARROW_X = INPUT_X + SLOT + 10;          // 46
    private static final int ARROW_WIDTH = 24;
    private static final int OUTPUT_X = ARROW_X + ARROW_WIDTH + 10;  // 80

    // Vertical: 3 rows of 18 = 54,  text row below
    private static final int SLOT_AREA_Y = 4;
    private static final int SLOT_AREA_HEIGHT = SLOT * 3;            // 54

    // Arrow is centred vertically within the 3-row area (arrow is ~17px tall)
    private static final int ARROW_Y = SLOT_AREA_Y + (SLOT_AREA_HEIGHT - 17) / 2;  // ~22

    private static final int TIME_TEXT_Y = SLOT_AREA_Y + SLOT_AREA_HEIGHT + 6;     // 64
    private static final int HEIGHT = TIME_TEXT_Y + 12;                             // 76

    private static final int TEXT_COLOR = 0x404040;

    private final IDrawable icon;

    public FirmamentConversionCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.FIRMAMENT_CONVERSION_CORE.get()));
    }

    @Override
    public RecipeType<FirmamentConversionRecipe> getRecipeType() {
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
        return Component.translatable("jei.ae2lt.firmament_conversion.title");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FirmamentConversionRecipe recipe, IFocusGroup focuses) {
        // --- Inputs (max 3, vertically centred within the 3-row area) ---
        int inputCount = recipe.inputs().size();
        int inputOffsetY = (SLOT_AREA_HEIGHT - inputCount * SLOT) / 2;
        for (int index = 0; index < inputCount; index++) {
            var input = recipe.inputs().get(index);
            builder.addSlot(
                            RecipeIngredientRole.INPUT,
                            INPUT_X + 1,
                            SLOT_AREA_Y + inputOffsetY + index * SLOT + 1)
                    .setStandardSlotBackground()
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStacks(expandIngredient(input.ingredient(), input.count()))
                    .addRichTooltipCallback((recipeSlotView, tooltip) ->
                            LargeStackCountRenderer.appendCountTooltip(tooltip, input.count()));
        }

        // --- Outputs (max 4, 2×2 grid, vertically centred within the 3-row area) ---
        List<ItemStack> results = recipe.getResultStacks();
        int outputCount = results.size();
        int outputCols = Math.min(outputCount, 2);
        int outputRows = (outputCount + 1) / 2;
        int outputOffsetY = (SLOT_AREA_HEIGHT - outputRows * SLOT) / 2;
        int outputOffsetX = (2 * SLOT - outputCols * SLOT) / 2; // centre 1-col within 2-col area
        for (int index = 0; index < outputCount; index++) {
            ItemStack result = results.get(index);
            int col = index % 2;
            int row = index / 2;
            builder.addSlot(
                            RecipeIngredientRole.OUTPUT,
                            OUTPUT_X + outputOffsetX + col * SLOT + 1,
                            SLOT_AREA_Y + outputOffsetY + row * SLOT + 1)
                    .setStandardSlotBackground()
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStack(result)
                    .addRichTooltipCallback((recipeSlotView, tooltip) ->
                            LargeStackCountRenderer.appendCountTooltip(tooltip, result.getCount()));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, FirmamentConversionRecipe recipe, IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(ARROW_X, ARROW_Y);
    }

    @Override
    public void draw(
            FirmamentConversionRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        var font = Minecraft.getInstance().font;

        String timeStr = formatProcessTime(recipe.processTime());
        var timeText = Component.translatable("jei.ae2lt.firmament_conversion.time", timeStr);
        int timeX = (WIDTH - font.width(timeText)) / 2;
        guiGraphics.drawString(font, timeText, timeX, TIME_TEXT_Y, TEXT_COLOR, false);
    }

    private static List<ItemStack> expandIngredient(Ingredient ingredient, int count) {
        return Arrays.stream(ingredient.getItems())
                .map(stack -> stack.copyWithCount(count))
                .toList();
    }

    private static String formatProcessTime(int ticks) {
        double seconds = ticks / 20.0;
        if (seconds == Math.floor(seconds)) {
            return (int) seconds + "s";
        }
        return String.format("%.1fs", seconds);
    }
}
