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
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.integration.jei.LargeStackJeiItemRenderer;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
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

public class CrystalCatalyzerCategory implements IRecipeCategory<CrystalCatalyzerRecipe> {
    public static final RecipeType<CrystalCatalyzerRecipe> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "crystal_catalyzer", CrystalCatalyzerRecipe.class);

    private static final ResourceLocation BACKGROUND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/guis/crystal_catalyzer.png");

    // 机器 GUI 坐标:流体腔 (26,18)+16×53,催化剂槽 (56,30),矩阵槽 (84,54),
    // 进度条 (74,33)+22×16,产物槽 (117,30),能量条 (140,30)+6×18。
    // 从 (22,14) 起裁剪 128×62,保留水箱/电池外框的 1~2px 装饰边。
    private static final int BACKGROUND_U = 22;
    private static final int BACKGROUND_V = 14;
    private static final int BACKGROUND_WIDTH = 128;
    private static final int BACKGROUND_HEIGHT = 62;
    private static final int WIDTH = BACKGROUND_WIDTH;

    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    // category 坐标 = GUI 坐标 − 背景偏移
    private static final int FLUID_X = 26 - BACKGROUND_U;   // 4
    private static final int FLUID_Y = 18 - BACKGROUND_V;   // 4
    private static final int FLUID_WIDTH = 16;
    private static final int FLUID_HEIGHT = 53;

    private static final int CATALYST_X = 56 - BACKGROUND_U; // 34
    private static final int CATALYST_Y = 30 - BACKGROUND_V; // 16
    private static final int OUTPUT_X = 117 - BACKGROUND_U;  // 95
    private static final int OUTPUT_Y = 30 - BACKGROUND_V;   // 16

    private static final int PROCESS_X = 74 - BACKGROUND_U;  // 52
    private static final int PROCESS_Y = 33 - BACKGROUND_V;  // 19
    private static final int PROCESS_OVERLAY_U = 176;
    private static final int PROCESS_OVERLAY_V = 18;
    private static final int PROCESS_OVERLAY_WIDTH = 35;
    private static final int PROCESS_OVERLAY_HEIGHT = 10;
    private static final long PROCESS_CYCLE_MS_CRYSTAL = 1_000L;
    private static final long PROCESS_CYCLE_MS_DUST = 2_000L;

    private static final int ENERGY_TEXT_Y = BACKGROUND_HEIGHT + 2;       // 64
    private static final int TIME_TEXT_Y = BACKGROUND_HEIGHT + 12;       // 74
    private static final int LIGHTNING_TEXT_Y = BACKGROUND_HEIGHT + 22;  // 84
    private static final int MATRIX_LINE1_Y = BACKGROUND_HEIGHT + 32;   // 94
    private static final int MATRIX_LINE2_Y = BACKGROUND_HEIGHT + 42;   // 104
    private static final int HEIGHT = MATRIX_LINE2_Y + 10;              // 114

    private final IDrawable icon;
    private final IDrawable background;

    public CrystalCatalyzerCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.CRYSTAL_CATALYZER.get()));
        this.background = guiHelper.createDrawable(
                BACKGROUND_TEXTURE, BACKGROUND_U, BACKGROUND_V, BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
    }

    @Override
    public RecipeType<CrystalCatalyzerRecipe> getRecipeType() {
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
        return Component.translatable("jei.ae2lt.crystal_catalyzer.title");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CrystalCatalyzerRecipe recipe, IFocusGroup focuses) {
        // 水现在是机器级固定开销,所有配方共用一份 1000 mB 的 water。
        var fluid = CrystalCatalyzerBlockEntity.getFixedFluidPerCycle();
        int fluidDisplayCapacity = Math.max(1, fluid.getAmount());
        builder.addSlot(RecipeIngredientRole.INPUT, FLUID_X, FLUID_Y)
                .setFluidRenderer(fluidDisplayCapacity, false, FLUID_WIDTH, FLUID_HEIGHT)
                .addIngredient(NeoForgeTypes.FLUID_STACK, fluid)
                .addRichTooltipCallback((slotView, tooltip) -> tooltip.add(
                        Component.translatable(
                                "jei.ae2lt.crystal_catalyzer.fluid_fixed",
                                fluid.getAmount())));

        recipe.catalyst().ifPresent(catalyst -> {
            int perInstance = Math.max(1, recipe.catalystCount());
            builder.addSlot(RecipeIngredientRole.INPUT, CATALYST_X, CATALYST_Y)
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStacks(expandIngredient(catalyst, perInstance))
                    .addRichTooltipCallback((recipeSlotView, tooltip) -> {
                        LargeStackCountRenderer.appendCountTooltip(tooltip, perInstance);
                        tooltip.add(Component.translatable(
                                "jei.ae2lt.crystal_catalyzer.catalyst_parallel",
                                perInstance,
                                CrystalCatalyzerInventory.CATALYST_SLOT_LIMIT));
                    });
        });

        var baseOutput = recipe.getOutputTemplate();
        int matrixMultiplier = CrystalCatalyzerBlockEntity.MATRIX_OUTPUT_MULTIPLIER;
        int baseCount = baseOutput.getCount();
        int catalystPerInstance = Math.max(1, recipe.catalystCount());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                .addItemStack(baseOutput)
                .addRichTooltipCallback((recipeSlotView, tooltip) -> {
                    LargeStackCountRenderer.appendCountTooltip(tooltip, baseCount);
                    tooltip.add(Component.translatable(
                            "jei.ae2lt.crystal_catalyzer.output_base", baseCount, matrixMultiplier));
                    tooltip.add(Component.translatable(
                            "jei.ae2lt.crystal_catalyzer.output_parallel", catalystPerInstance));
                });
    }

    @Override
    public void draw(
            CrystalCatalyzerRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        background.draw(guiGraphics);
        drawProcessOverlay(guiGraphics, recipe.mode());

        var font = Minecraft.getInstance().font;

        var energyText = Component.translatable(
                "jei.ae2lt.crystal_catalyzer.energy",
                formatCompactEnergy(recipe.energyPerCycle()));
        int energyX = (WIDTH - font.width(energyText)) / 2;
        guiGraphics.drawString(font, energyText, energyX, ENERGY_TEXT_Y, 0x404040, false);

        String timeStr = recipe.mode() == Mode.CRYSTAL ? "1s" : "2s";
        var timeText = Component.translatable(
                "jei.ae2lt.crystal_catalyzer.time", timeStr);
        int timeX = (WIDTH - font.width(timeText)) / 2;
        guiGraphics.drawString(font, timeText, timeX, TIME_TEXT_Y, 0x404040, false);

        var lightningText = Component.translatable(
                "jei.ae2lt.crystal_catalyzer.lightning",
                recipe.lightningCost(),
                Component.translatable(recipe.lightningTier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                        ? "ae2lt.gui.lightning_simulation.tier.extreme_high_voltage"
                        : "ae2lt.gui.lightning_simulation.tier.high_voltage"));
        int lightningX = (WIDTH - font.width(lightningText)) / 2;
        guiGraphics.drawString(font, lightningText, lightningX, LIGHTNING_TEXT_Y, 0x404040, false);

        var matrixLine1 = Component.translatable("jei.ae2lt.crystal_catalyzer.matrix_note_line1");
        int matrixLine1X = (WIDTH - font.width(matrixLine1)) / 2;
        guiGraphics.drawString(font, matrixLine1, matrixLine1X, MATRIX_LINE1_Y, 0x404040, false);

        var matrixLine2 = Component.translatable(
                "jei.ae2lt.crystal_catalyzer.matrix_note_line2",
                CrystalCatalyzerBlockEntity.MATRIX_OUTPUT_MULTIPLIER);
        int matrixLine2X = (WIDTH - font.width(matrixLine2)) / 2;
        guiGraphics.drawString(font, matrixLine2, matrixLine2X, MATRIX_LINE2_Y, 0x404040, false);
    }

    private void drawProcessOverlay(GuiGraphics guiGraphics, Mode mode) {
        long cycleMs = mode == Mode.CRYSTAL ? PROCESS_CYCLE_MS_CRYSTAL : PROCESS_CYCLE_MS_DUST;
        long elapsed = Util.getMillis() % cycleMs;
        double progress = elapsed / (double) cycleMs;
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
