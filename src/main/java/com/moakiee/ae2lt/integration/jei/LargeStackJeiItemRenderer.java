package com.moakiee.ae2lt.integration.jei;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;

import mezz.jei.api.ingredients.IIngredientRenderer;

public class LargeStackJeiItemRenderer implements IIngredientRenderer<ItemStack> {
    public static final LargeStackJeiItemRenderer INSTANCE = new LargeStackJeiItemRenderer();

    private LargeStackJeiItemRenderer() {
    }

    @Override
    public void render(GuiGraphics guiGraphics, ItemStack ingredient) {
        render(guiGraphics, ingredient, 0, 0);
    }

    @Override
    public void render(GuiGraphics guiGraphics, ItemStack ingredient, int posX, int posY) {
        if (ingredient == null || ingredient.isEmpty()) {
            return;
        }

        // Intentionally do NOT toggle RenderSystem.enableDepthTest / disableBlend here.
        // GuiGraphics#renderFakeItem and Font#drawInBatch already manage their own
        // depth / blend state via their RenderTypes; toggling them here would leave
        // the GL state machine in an unexpected configuration for the next JEI
        // ingredient (one of the patterns the optimization report flags as
        // GL state-machine pollution).
        guiGraphics.renderFakeItem(ingredient, posX, posY);
        LargeStackCountRenderer.renderCountAt(guiGraphics, getFontRenderer(Minecraft.getInstance(), ingredient), posX, posY, ingredient.getCount());
    }

    @Override
    public List<Component> getTooltip(ItemStack ingredient, TooltipFlag tooltipFlag) {
        Minecraft minecraft = Minecraft.getInstance();
        Item.TooltipContext tooltipContext = Item.TooltipContext.of(minecraft.level);
        return ingredient.getTooltipLines(tooltipContext, minecraft.player, tooltipFlag);
    }

    @Override
    public Font getFontRenderer(Minecraft minecraft, ItemStack ingredient) {
        return minecraft.font;
    }
}
