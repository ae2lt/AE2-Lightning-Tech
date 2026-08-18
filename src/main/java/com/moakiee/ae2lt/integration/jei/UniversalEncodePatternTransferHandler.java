package com.moakiee.ae2lt.integration.jei;

import appeng.integration.modules.jei.transfer.EncodePatternTransferHandler;
import appeng.menu.me.items.PatternEncodingTermMenu;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes AE2's 1.20.1 pattern encoder through JEI's current universal-transfer API.
 *
 * <p>AE2 15.4 implements the legacy {@code IRecipeTransferHandler} interface, but
 * its container selection and transfer method already have the exact contract
 * required by {@link IUniversalRecipeTransferHandler}. This adapter changes only
 * the registration type and keeps AE2's validated encoding implementation.</p>
 */
final class UniversalEncodePatternTransferHandler<T extends PatternEncodingTermMenu>
        implements IUniversalRecipeTransferHandler<T> {
    private final EncodePatternTransferHandler<T> delegate;

    UniversalEncodePatternTransferHandler(
            MenuType<T> menuType,
            Class<T> menuClass,
            IRecipeTransferHandlerHelper helper) {
        this.delegate = new EncodePatternTransferHandler<>(menuType, menuClass, helper);
    }

    @Override
    public Class<? extends T> getContainerClass() {
        return delegate.getContainerClass();
    }

    @Override
    public Optional<MenuType<T>> getMenuType() {
        return delegate.getMenuType();
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(
            T container,
            Object recipe,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        return delegate.transferRecipe(container, recipe, recipeSlots, player, maxTransfer, doTransfer);
    }
}
