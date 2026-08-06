package com.moakiee.ae2lt.integration.jei.category;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.integration.jei.LargeStackJeiItemRenderer;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilMode;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

public class TeslaCoilCategory implements IRecipeCategory<TeslaCoilCategory.Page> {
    public static final RecipeType<Page> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "tesla_coil", Page.class);

    private static final int WIDTH = 150;
    private static final int HEIGHT = 90;

    private static final int ROW_Y = 8;
    private static final int INPUT_X = 12;
    private static final int ARROW_X = 54;
    private static final int ARROW_Y = ROW_Y + 3;
    private static final int ARROW_W = 35;
    private static final int ARROW_H = 10;
    private static final int OUTPUT_X = 118;
    private static final int OUTPUT_Y = ROW_Y;
    private static final int ICON_SIZE = 16;
    private static final int ICON_FRAME_H = 16;
    private static final int ICON_SHEET_H = 96;
    private static final int ICON_FRAMES = 6;
    private static final long ICON_FRAME_MS = 100L;
    private static final long PROCESS_CYCLE_MS = 1500L;

    private static final int TEXT_COLOR = 0x404040;
    private static final int[] TEXT_LINES = {34, 46, 58, 70};

    private static final ResourceLocation ARROW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/guis/crystal_catalyzer.png");
    private static final int ARROW_U = 176;
    private static final int ARROW_V = 18;
    private static final int ARROW_TEX_W = 256;
    private static final int ARROW_TEX_H = 256;

    private static final ResourceLocation HV_LIGHTNING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/item/high_voltage_lightning.png");
    private static final ResourceLocation EHV_LIGHTNING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/item/extreme_high_voltage_lightning.png");

    private final IDrawable icon;

    public TeslaCoilCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.TESLA_COIL.get()));
    }

    @Override
    public RecipeType<Page> getRecipeType() {
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
        return Component.translatable("jei.ae2lt.tesla_coil.title");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Page page, IFocusGroup focuses) {
        TeslaCoilMode mode = page.mode;
        if (mode == TeslaCoilMode.HIGH_VOLTAGE) {
            int dustCount = Math.max(1, mode.requiredDust());
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, ROW_Y)
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, LargeStackJeiItemRenderer.INSTANCE)
                    .addItemStack(new ItemStack(ModItems.OVERLOAD_CRYSTAL_DUST.get(), dustCount))
                    .addRichTooltipCallback((slotView, tooltip) ->
                            LargeStackCountRenderer.appendCountTooltip(tooltip, dustCount));
        }
    }

    @Override
    public void draw(
            Page page,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY) {
        TeslaCoilMode mode = page.mode;
        Font font = Minecraft.getInstance().font;

        drawArrow(guiGraphics);
        drawLightningIcon(
                guiGraphics,
                mode == TeslaCoilMode.EXTREME_HIGH_VOLTAGE ? EHV_LIGHTNING_TEXTURE : HV_LIGHTNING_TEXTURE,
                OUTPUT_X,
                OUTPUT_Y);

        Component modeText = Component.translatable(
                "jei.ae2lt.tesla_coil.mode_label",
                Component.translatable(mode.translationKey()));
        drawCentered(guiGraphics, font, modeText, TEXT_LINES[0]);

        Component energyText = Component.translatable(
                "jei.ae2lt.tesla_coil.energy",
                formatCompactEnergy(mode.totalEnergy()));
        drawCentered(guiGraphics, font, energyText, TEXT_LINES[1]);

        Component inputText;
        if (mode == TeslaCoilMode.HIGH_VOLTAGE) {
            inputText = Component.translatable(
                    "jei.ae2lt.tesla_coil.consume_dust",
                    mode.requiredDust());
        } else {
            inputText = Component.translatable(
                    "jei.ae2lt.tesla_coil.consume_hv",
                    mode.requiredHighVoltage());
        }
        drawCentered(guiGraphics, font, inputText, TEXT_LINES[2]);

        Component outputText = Component.translatable(
                mode == TeslaCoilMode.EXTREME_HIGH_VOLTAGE
                        ? "jei.ae2lt.tesla_coil.output_ehv"
                        : "jei.ae2lt.tesla_coil.output_hv");
        drawCentered(guiGraphics, font, outputText, TEXT_LINES[3]);
    }

    private static void drawCentered(GuiGraphics guiGraphics, Font font, Component text, int y) {
        int x = (WIDTH - font.width(text)) / 2;
        guiGraphics.drawString(font, text, x, y, TEXT_COLOR, false);
    }

    private static void drawArrow(GuiGraphics guiGraphics) {
        long elapsed = Util.getMillis() % PROCESS_CYCLE_MS;
        double progress = elapsed / (double) PROCESS_CYCLE_MS;
        int fillW = Mth.clamp((int) Math.ceil(progress * ARROW_W), 0, ARROW_W);
        if (fillW <= 0) {
            return;
        }
        guiGraphics.blit(
                ARROW_TEXTURE,
                ARROW_X,
                ARROW_Y,
                ARROW_U,
                ARROW_V,
                fillW,
                ARROW_H,
                ARROW_TEX_W,
                ARROW_TEX_H);
    }

    private static void drawLightningIcon(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y) {
        int frame = (int) ((Util.getMillis() / ICON_FRAME_MS) % ICON_FRAMES);
        guiGraphics.blit(
                texture,
                x,
                y,
                0,
                frame * ICON_FRAME_H,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SHEET_H);
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

    public enum Page {
        HIGH_VOLTAGE(TeslaCoilMode.HIGH_VOLTAGE),
        EXTREME_HIGH_VOLTAGE(TeslaCoilMode.EXTREME_HIGH_VOLTAGE);

        public final TeslaCoilMode mode;

        Page(TeslaCoilMode mode) {
            this.mode = mode;
        }
    }
}
