package com.moakiee.ae2lt.integration.jei;

import java.util.ArrayList;
import java.util.List;

import com.moakiee.ae2lt.me.key.LightningKey;

import appeng.api.client.AEKeyRendering;
import appeng.util.Platform;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

public class LightningJeiIngredientRenderer implements IIngredientRenderer<LightningKey> {
    public static final IIngredientRenderer<LightningKey> NO_TOOLTIP = new LightningJeiIngredientRenderer() {
        @Override
        public List<Component> getTooltip(LightningKey ingredient, TooltipFlag tooltipFlag) {
            return List.of();
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip, LightningKey ingredient, TooltipFlag tooltipFlag) {
        }
    };

    @Override
    public void render(GuiGraphics guiGraphics, LightningKey ingredient) {
        render(guiGraphics, ingredient, 0, 0);
    }

    @Override
    public void render(GuiGraphics guiGraphics, LightningKey ingredient, int posX, int posY) {
        if (ingredient == null) {
            return;
        }

        AEKeyRendering.drawInGui(Minecraft.getInstance(), guiGraphics, posX, posY, ingredient);
    }

    @Override
    public List<Component> getTooltip(LightningKey ingredient, TooltipFlag tooltipFlag) {
        return AEKeyRendering.getTooltip(ingredient);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, LightningKey ingredient, TooltipFlag tooltipFlag) {
        tooltip.addAll(getJeiTooltip(ingredient));
    }

    @Override
    public Font getFontRenderer(Minecraft minecraft, LightningKey ingredient) {
        return minecraft.font;
    }

    private static List<Component> getJeiTooltip(LightningKey ingredient) {
        var tooltip = new ArrayList<>(AEKeyRendering.getTooltip(ingredient));
        if (tooltip.isEmpty()) {
            return tooltip;
        }

        var modName = Platform.formatModName(ingredient.getModId());
        var lastLine = tooltip.get(tooltip.size() - 1).getString();
        if (lastLine.equals(modName)) {
            tooltip.remove(tooltip.size() - 1);
        }

        return tooltip;
    }
}
