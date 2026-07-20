package com.moakiee.ae2lt.mixin.recipeviewer.jei;

/*
 * Adapted from ExtendedAE Plus [ClientPlus] at revision
 * 07f8373c590c0c6d845f794e7c25090e5ef5703e. SPDX-License-Identifier: LGPL-3.0-only
 */

import com.moakiee.ae2lt.client.TianshuRecipeTransferContext;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tamaized.ae2jeiintegration.integration.modules.jei.transfer.EncodePatternTransferHandler;

/** Captures JEI recipe metadata without changing AE2's normal encoding lifecycle. */
@Mixin(value = EncodePatternTransferHandler.class, remap = false)
public abstract class JeiEncodePatternTransferMixin {
    @Inject(
            method = "transferRecipe(Lnet/minecraft/world/inventory/AbstractContainerMenu;"
                    + "Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;"
                    + "Lnet/minecraft/world/entity/player/Player;ZZ)"
                    + "Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("HEAD"),
            require = 0)
    private void ae2lt$onTransfer(
            AbstractContainerMenu menu,
            Object recipeBase,
            IRecipeSlotsView slotsView,
            Player player,
            boolean maxTransfer,
            boolean doTransfer,
            CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (!doTransfer || !(menu instanceof TianshuPatternEncodingTermMenu tianshuMenu)) return;
        // Match ClientPlus' JEMI ownership rule so one transfer cannot be recorded twice.
        if (ModList.get().isLoaded("emi")) return;
        if (!TianshuRecipeTransferContext.isSupportedCraftingRecipe(recipeBase)) {
            TianshuRecipeTransferContext.captureVanillaRecipe(tianshuMenu, recipeBase);
        }
    }
}
