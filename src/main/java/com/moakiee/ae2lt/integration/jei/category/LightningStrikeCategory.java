package com.moakiee.ae2lt.integration.jei.category;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.integration.jei.LightningJeiIngredients;
import com.moakiee.ae2lt.integration.jei.MultiblockPreviewWidget;
import com.moakiee.ae2lt.lightning.strike.LightningStrikeRecipe;
import com.moakiee.ae2lt.lightning.strike.StructureRequirement;
import com.moakiee.ae2lt.me.key.LightningKey;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * JEI category for the data-driven multiblock lightning-strike recipes.
 *
 * <p>The category renders a small isometric 3D preview of the multiblock
 * structure on the left and a column with the consumed center input,
 * the produced center output and the unique material blocks on the right.</p>
 */
public class LightningStrikeCategory implements IRecipeCategory<LightningStrikeRecipe> {
    public static final RecipeType<LightningStrikeRecipe> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "lightning_strike", LightningStrikeRecipe.class);

    private static final int WIDTH = 178;
    private static final int HEIGHT = 110;

    private static final int PREVIEW_X = 4;
    private static final int PREVIEW_Y = 14;
    private static final int PREVIEW_W = 96;
    private static final int PREVIEW_H = 92;

    private static final int CENTER_INPUT_X = 104;
    private static final int CENTER_INPUT_Y = 14;
    private static final int ARROW_X = 124;
    private static final int ARROW_Y = 15;
    private static final int CENTER_OUTPUT_X = 156;
    private static final int CENTER_OUTPUT_Y = 14;

    private static final int MATERIALS_LABEL_Y = 38;
    private static final int MATERIALS_X = 104;
    private static final int MATERIALS_Y = 50;
    private static final int MATERIAL_CELL = 18;
    private static final int MATERIALS_PER_ROW = 4;

    private static final int TEXT_COLOR = 0x404040;

    private final IDrawable background;
    private final IDrawable icon;

    public LightningStrikeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(LightningJeiIngredients.TYPE, LightningKey.HIGH_VOLTAGE);
    }

    @Override
    public RecipeType<LightningStrikeRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ae2lt.lightning_strike.title");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LightningStrikeRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, CENTER_INPUT_X, CENTER_INPUT_Y)
                .setStandardSlotBackground()
                .addItemStack(new ItemStack(recipe.centerInput()));

        builder.addSlot(RecipeIngredientRole.OUTPUT, CENTER_OUTPUT_X, CENTER_OUTPUT_Y)
                .setOutputSlotBackground()
                .addItemStack(new ItemStack(recipe.centerOutput()));

        // Aggregate the requirements by block so each unique block is shown once
        // with the total count needed across the structure. Insertion order is
        // preserved so the layout matches the recipe's declaration order.
        Map<Block, Integer> blockCounts = new LinkedHashMap<>();
        Map<Block, Boolean> blockConsumes = new HashMap<>();
        for (StructureRequirement req : recipe.requirements()) {
            blockCounts.merge(req.block(), 1, Integer::sum);
            blockConsumes.merge(req.block(), req.consume(), (a, b) -> a || b);
        }

        int index = 0;
        for (Map.Entry<Block, Integer> entry : blockCounts.entrySet()) {
            Block block = entry.getKey();
            int count = entry.getValue();
            int col = index % MATERIALS_PER_ROW;
            int row = index / MATERIALS_PER_ROW;
            int slotX = MATERIALS_X + col * MATERIAL_CELL;
            int slotY = MATERIALS_Y + row * MATERIAL_CELL;
            builder.addSlot(
                            blockConsumes.getOrDefault(block, false)
                                    ? RecipeIngredientRole.INPUT
                                    : RecipeIngredientRole.CATALYST,
                            slotX,
                            slotY)
                    .setStandardSlotBackground()
                    .addItemStack(new ItemStack(block, count));
            index++;
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, LightningStrikeRecipe recipe, IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(ARROW_X, ARROW_Y);

        var widgetBuilder = MultiblockPreviewWidget.builder(PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H);

        // y=0 layer requirements at their world offsets.
        for (StructureRequirement req : recipe.requirements()) {
            widgetBuilder.addBlock(req.block(), req.offset());
        }
        // The consumed center block.
        widgetBuilder.addBlock(recipe.centerInput(), BlockPos.ZERO);
        // The implicit lightning rod at (0, +1, 0).
        widgetBuilder.addBlock(Blocks.LIGHTNING_ROD, new BlockPos(0, 1, 0));

        builder.addWidget(widgetBuilder.build());
    }

    @Override
    public void draw(
            LightningStrikeRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        var font = Minecraft.getInstance().font;

        Component lightningLabel = recipe.requiresNaturalLightning()
                ? Component.translatable("jei.ae2lt.lightning_strike.natural_only")
                        .withStyle(ChatFormatting.DARK_PURPLE)
                : Component.translatable("jei.ae2lt.lightning_strike.any_lightning")
                        .withStyle(ChatFormatting.DARK_AQUA);
        guiGraphics.drawString(font, lightningLabel, PREVIEW_X, 2, TEXT_COLOR, false);

        guiGraphics.drawString(
                font,
                Component.translatable("jei.ae2lt.lightning_strike.materials"),
                MATERIALS_X,
                MATERIALS_LABEL_Y,
                TEXT_COLOR,
                false);
    }
}
