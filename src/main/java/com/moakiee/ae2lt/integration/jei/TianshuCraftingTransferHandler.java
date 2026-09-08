package com.moakiee.ae2lt.integration.jei;

import appeng.integration.modules.itemlists.CraftingHelper;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.Nullable;

/** Direct JEI API integration, also available without the separate AE2 JEI addon. */
public final class TianshuCraftingTransferHandler<M extends TianshuCraftingTermMenu> implements IUniversalRecipeTransferHandler<M> {
    private final Class<M> menuClass;
    private final MenuType<M> menuType;
    private final IRecipeTransferHandlerHelper helper;
    public TianshuCraftingTransferHandler(Class<M> menuClass, MenuType<M> menuType, IRecipeTransferHandlerHelper helper) {
        this.menuClass = menuClass; this.menuType = menuType; this.helper = helper;
    }
    @Override public Class<M> getContainerClass() { return menuClass; }
    @Override public Optional<MenuType<M>> getMenuType() { return Optional.of(menuType); }
    @Nullable @Override public IRecipeTransferError transferRecipe(M menu, Object displayedRecipe, IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (!menu.canUseWorkstations()) return helper.createInternalError();
        if (!(displayedRecipe instanceof RecipeHolder<?> holder)) return helper.createInternalError();
        if (holder.value() instanceof CraftingRecipe crafting) {
            if (!crafting.canCraftInDimensions(3, 3)) return helper.createUserErrorWithTooltip(Component.translatable("ae2lt.tianshu.work.recipe_too_large"));
            var recipe = new RecipeHolder<>(holder.id(), crafting);
            var ingredients = helper.getGuiSlotIndexToIngredientMap(recipe);
            var missing = menu.findMissingIngredients(ingredients);
            if (!ingredients.isEmpty() && missing.missingSlots().size() == ingredients.size()) return missingItems();
            if (doTransfer) {
                menu.prepareCraftingTransfer();
                CraftingHelper.performTransfer(menu, holder.id(), crafting, AbstractContainerScreen.hasControlDown());
            }
            return null;
        }
        if (!(holder.value() instanceof SmithingRecipe || holder.value() instanceof StonecutterRecipe)) return helper.createInternalError();
        if (!menu.canFillWorkRecipe(holder.value())) return missingItems();
        if (doTransfer) menu.fillWorkRecipe(holder.id().toString());
        return null;
    }
    private IRecipeTransferError missingItems() { return helper.createUserErrorWithTooltip(Component.translatable("ae2lt.tianshu.work.no_materials")); }
}
