package com.moakiee.ae2lt.integration.jei.category;

import java.util.Arrays;
import java.util.List;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.integration.jei.LargeStackJeiItemRenderer;
import com.moakiee.ae2lt.integration.jei.LightningJeiIngredientRenderer;
import com.moakiee.ae2lt.integration.jei.LightningJeiIngredients;
import com.moakiee.ae2lt.lightning.LightningTransformRecipe;
import com.moakiee.ae2lt.me.key.LightningKey;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public class LightningTransformCategory implements IRecipeCategory<LightningTransformRecipe> {
    public static final RecipeType<LightningTransformRecipe> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "lightning_transform", LightningTransformRecipe.class);

    private static final int WIDTH = 134;
    private static final int HEIGHT = 66;
    private static final int INPUT_START_X = 5;
    private static final int INPUT_START_Y = 5;
    private static final int INPUT_SLOT_PITCH = 20;
    private static final int CATALYST_X = 56;
    private static final int CATALYST_Y = 25;
    private static final int OUTPUT_X = 110;
    private static final int OUTPUT_Y = 25;
    private static final int ARROW_LEFT_X = 28;
    private static final int ARROW_RIGHT_X = 81;
    private static final int ARROW_Y = 24;
    private static final int LABEL_Y = 4;
    private static final int TEXT_COLOR = 0x404040;

    private final IDrawable background;
    private final IDrawable icon;

    public LightningTransformCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(LightningJeiIngredients.TYPE, LightningKey.HIGH_VOLTAGE);
    }

    @Override
    public RecipeType<LightningTransformRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ae2lt.lightning_transform.title");
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
    public void setRecipe(IRecipeLayoutBuilder builder, LightningTransformRecipe recipe, IFocusGroup focuses) {
        int inputCount = recipe.inputs().size();
        int x = INPUT_START_X;
        int y = INPUT_START_Y;
        if (inputCount < 3) {
            y += (3 - inputCount) * INPUT_SLOT_PITCH / 2;
        }
        for (int index = 0; index < inputCount; index++) {
            var input = recipe.inputs().get(index);
            builder.addSlot(RecipeIngredientRole.INPUT, x + 1, y + 1)
                    .setStandardSlotBackground()
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStacks(expandIngredient(input.ingredient(), input.count()))
                    .addRichTooltipCallback((recipeSlotView, tooltip) ->
                            LargeStackCountRenderer.appendCountTooltip(tooltip, input.count()));
            y += INPUT_SLOT_PITCH;
            if (y >= INPUT_START_Y + INPUT_SLOT_PITCH * 3) {
                y -= INPUT_SLOT_PITCH * 3;
                x += 18;
            }
        }

        builder.addSlot(RecipeIngredientRole.CATALYST, CATALYST_X + 1, CATALYST_Y + 1)
                .setStandardSlotBackground()
                .setCustomRenderer(LightningJeiIngredients.TYPE, LightningJeiIngredientRenderer.NO_TOOLTIP)
                .addIngredient(LightningJeiIngredients.TYPE, LightningKey.HIGH_VOLTAGE);

        var resultStack = recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + 1, OUTPUT_Y + 1)
                .setOutputSlotBackground()
                .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                .addItemStack(resultStack)
                .addRichTooltipCallback((recipeSlotView, tooltip) ->
                        LargeStackCountRenderer.appendCountTooltip(tooltip, resultStack.getCount()));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, LightningTransformRecipe recipe, IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(ARROW_LEFT_X, ARROW_Y);
        builder.addRecipeArrow().setPosition(ARROW_RIGHT_X, ARROW_Y);
    }

    @Override
    public void draw(
            LightningTransformRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        var font = Minecraft.getInstance().font;
        var label = Component.translatable("jei.ae2lt.lightning_transform.label");
        int labelX = (WIDTH - font.width(label)) / 2;
        guiGraphics.drawString(font, label, labelX, LABEL_Y, TEXT_COLOR, false);
    }

    private static List<ItemStack> expandIngredient(Ingredient ingredient, int count) {
        return Arrays.stream(ingredient.getItems())
                .map(stack -> stack.copyWithCount(count))
                .toList();
    }
}
