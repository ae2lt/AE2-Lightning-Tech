package com.moakiee.ae2lt.integration.emi;

import appeng.integration.modules.emi.EmiUseCraftingRecipeHandler;
import com.moakiee.ae2lt.client.TianshuCraftingTermScreen;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

public final class TianshuCraftingRecipeHandler<M extends TianshuCraftingTermMenu> extends EmiUseCraftingRecipeHandler<M> {
    public TianshuCraftingRecipeHandler(Class<M> menuClass) { super(menuClass); }
    @Override public boolean supportsRecipe(EmiRecipe recipe) {
        return super.supportsRecipe(recipe) || recipe.getCategory().equals(VanillaEmiRecipeCategories.SMITHING)
                || recipe.getCategory().equals(VanillaEmiRecipeCategories.STONECUTTING);
    }
    @Nullable private RecipeHolder<?> holder(EmiRecipe recipe, M menu) {
        if (recipe.getBackingRecipe() != null) return recipe.getBackingRecipe();
        return recipe.getId() == null ? null : menu.getPlayer().level().getRecipeManager().byKey(recipe.getId()).orElse(null);
    }
    @Override public boolean canCraft(EmiRecipe recipe, EmiCraftContext<M> context) {
        if (!(context.getScreen() instanceof TianshuCraftingTermScreen<?>)) return false;
        if (super.supportsRecipe(recipe)) return super.canCraft(recipe, context);
        var holder = holder(recipe, context.getScreenHandler());
        return holder != null && context.getScreenHandler().canFillWorkRecipe(holder.value());
    }
    @Override public boolean craft(EmiRecipe recipe, EmiCraftContext<M> context) {
        if (!canCraft(recipe, context)) return false;
        if (super.supportsRecipe(recipe)) {
            context.getScreenHandler().prepareCraftingTransfer();
            return super.craft(recipe, context);
        }
        var holder = holder(recipe, context.getScreenHandler());
        if (holder == null) return false;
        context.getScreenHandler().fillWorkRecipe(holder.id().toString());
        Minecraft.getInstance().setScreen(context.getScreen());
        return true;
    }
    @Override public java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<M> context) {
        return super.supportsRecipe(recipe) ? super.getTooltip(recipe, context) : java.util.List.of();
    }
    @Override public void render(EmiRecipe recipe, EmiCraftContext<M> context,
            java.util.List<dev.emi.emi.api.widget.Widget> widgets, net.minecraft.client.gui.GuiGraphics graphics) {
        if (super.supportsRecipe(recipe)) super.render(recipe, context, widgets, graphics);
    }
}
