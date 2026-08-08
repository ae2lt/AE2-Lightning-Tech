package com.moakiee.ae2lt.mixin.recipeviewer.jei;

/*
 * Adapted from ExtendedAE Plus [ClientPlus] at revision
 * 07f8373c590c0c6d845f794e7c25090e5ef5703e. SPDX-License-Identifier: LGPL-3.0-only
 */

import com.moakiee.ae2lt.client.JeiRecipeTransferMetadata;
import com.moakiee.ae2lt.client.TianshuRecipeTransferContext;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import appeng.integration.modules.jei.transfer.EncodePatternTransferHandler;

/**
 * Captures provider metadata normally and starts closed-loop discovery from recipe outputs.
 * The NeoForge port targeted ExtendedAE's standalone {@code EncodePatternTransferHandler};
 * Forge AE2 15.x embeds the same handler under {@code appeng.integration.modules.jei.transfer}.
 */
@Mixin(value = EncodePatternTransferHandler.class, remap = false)
public abstract class JeiEncodePatternTransferMixin {
    @Inject(
            method = "transferRecipe(Lnet/minecraft/world/inventory/AbstractContainerMenu;"
                    + "Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;"
                    + "Lnet/minecraft/world/entity/player/Player;ZZ)"
                    + "Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("HEAD"),
            cancellable = true,
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
        // An actual transfer starts a new metadata generation. Clear before every early return so
        // recipes without a discoverable type/ID can never inherit the previous recipe's ID.
        TianshuRecipeTransferContext.clear(tianshuMenu);
        if (tianshuMenu.tianshuMode == TianshuEncodingMode.CLOSED_LOOP) {
            var output = firstDisplayedItemOutput(slotsView);
            if (tianshuMenu.markClosedLoopPrimaryOutput(output)) {
                tianshuMenu.autoFillClosedLoop();
                cir.setReturnValue(null);
            }
            return;
        }
        tianshuMenu.resetProcessingEncoding();
        var fallback = JeiRecipeTransferMetadata.snapshotFor(tianshuMenu);
        TianshuRecipeTransferContext.captureVanillaRecipe(
                tianshuMenu,
                recipeBase,
                fallback.sourceKey(),
                fallback.defaultAliases());
    }

    @Inject(
            method = "transferRecipe(Lnet/minecraft/world/inventory/AbstractContainerMenu;"
                    + "Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;"
                    + "Lnet/minecraft/world/entity/player/Player;ZZ)"
                    + "Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("RETURN"),
            require = 0)
    private void ae2lt$encodeAndUploadAfterSuccessfulAltTransfer(
            AbstractContainerMenu menu,
            Object recipeBase,
            IRecipeSlotsView slotsView,
            Player player,
            boolean maxTransfer,
            boolean doTransfer,
            CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (!doTransfer
                || cir.getReturnValue() != null
                || ModList.get().isLoaded("emi")
                || !Screen.hasAltDown()
                || !(menu instanceof TianshuPatternEncodingTermMenu tianshuMenu)
                || tianshuMenu.tianshuMode == TianshuEncodingMode.CLOSED_LOOP) {
            return;
        }
        tianshuMenu.encodeAndUploadDirectly();
    }

    private static ItemStack firstDisplayedItemOutput(IRecipeSlotsView slotsView) {
        if (slotsView == null) return ItemStack.EMPTY;
        return slotsView.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .map(slot -> slot.getDisplayedIngredient(VanillaTypes.ITEM_STACK)
                        .orElse(ItemStack.EMPTY))
                .filter(stack -> !stack.isEmpty())
                .findFirst()
                .map(ItemStack::copy)
                .orElse(ItemStack.EMPTY);
    }
}
