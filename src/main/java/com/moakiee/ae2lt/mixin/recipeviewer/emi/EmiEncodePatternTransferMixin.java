package com.moakiee.ae2lt.mixin.recipeviewer.emi;

/*
 * Adapted from ExtendedAE Plus [ClientPlus] at revision
 * 07f8373c590c0c6d845f794e7c25090e5ef5703e. SPDX-License-Identifier: LGPL-3.0-only
 */

import appeng.integration.modules.emi.EmiEncodePatternHandler;
import appeng.integration.modules.emi.EmiStackHelper;
import appeng.menu.AEBaseMenu;
import com.moakiee.ae2lt.client.TianshuRecipeTransferContext;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import java.util.ArrayList;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures provider metadata normally and starts closed-loop discovery from recipe outputs. */
@Mixin(value = EmiEncodePatternHandler.class, remap = false)
public abstract class EmiEncodePatternTransferMixin {
    @Unique
    private static boolean ae2lt$restoreClosedLoopMode;

    @Inject(
            method = "transferRecipe(Lappeng/menu/AEBaseMenu;"
                    + "Lnet/minecraft/world/item/crafting/RecipeHolder;"
                    + "Ldev/emi/emi/api/recipe/EmiRecipe;Z)"
                    + "Lappeng/integration/modules/emi/AbstractRecipeHandler$Result;",
            at = @At("HEAD"),
            require = 0)
    private static void ae2lt$onTransfer(
            AEBaseMenu menu,
            RecipeHolder<?> holder,
            EmiRecipe emiRecipe,
            boolean doTransfer,
            CallbackInfoReturnable<Object> cir) {
        if (!doTransfer || !(menu instanceof TianshuPatternEncodingTermMenu tianshuMenu)) return;
        if (tianshuMenu.tianshuMode == TianshuEncodingMode.CLOSED_LOOP && emiRecipe != null) {
            var output = EmiStackHelper.ofOutputs(emiRecipe).stream().findFirst().orElse(null);
            if (output != null && tianshuMenu.markClosedLoopPrimaryOutput(
                    appeng.api.stacks.GenericStack.wrapInItemStack(output))) {
                tianshuMenu.autoFillClosedLoop();
                TianshuRecipeTransferContext.clear(tianshuMenu);
                // AE2's EMI result type is package-private, so let the normal transfer complete
                // and restore the Tianshu tab after its mode-change packet has been queued.
                ae2lt$restoreClosedLoopMode = true;
            }
            return;
        }
        tianshuMenu.resetProcessingEncoding();
        TianshuRecipeTransferContext.clear(tianshuMenu);
        if (holder != null && TianshuRecipeTransferContext.isSupportedCraftingRecipe(holder)) return;

        String sourceKey = "";
        String recipeId = "";
        var defaultAliases = new ArrayList<String>();
        var workstationAliases = new ArrayList<String>();
        if (emiRecipe != null) {
            var category = emiRecipe.getCategory();
            if (category != null) {
                sourceKey = category.getId().toString();
                TianshuRecipeTransferContext.addDefaultAlias(defaultAliases, sourceKey);
                if (category.getName() != null) {
                    TianshuRecipeTransferContext.addDefaultAlias(
                            defaultAliases, category.getName().getString());
                }
                for (var workstation : EmiApi.getRecipeManager().getWorkstations(category).reversed()) {
                    for (var stack : workstation.getEmiStacks().reversed()) {
                        if (stack.getName() != null) {
                            TianshuRecipeTransferContext.addDefaultAlias(
                                    workstationAliases, stack.getName().getString());
                        }
                    }
                }
            }
            if (emiRecipe.getId() != null) {
                recipeId = emiRecipe.getId().toString();
                TianshuRecipeTransferContext.addDefaultAlias(
                        defaultAliases,
                        TianshuRecipeTransferContext.firstPathSegment(emiRecipe.getId().getPath()));
            }
            workstationAliases.forEach(alias ->
                    TianshuRecipeTransferContext.addDefaultAlias(defaultAliases, alias));
        }
        if (sourceKey.isBlank() && recipeId.isBlank() && holder != null) {
            TianshuRecipeTransferContext.captureVanillaRecipe(tianshuMenu, holder);
        } else {
            TianshuRecipeTransferContext.publish(
                    tianshuMenu, sourceKey, recipeId, defaultAliases);
        }
    }

    @Inject(
            method = "transferRecipe(Lappeng/menu/AEBaseMenu;"
                    + "Lnet/minecraft/world/item/crafting/RecipeHolder;"
                    + "Ldev/emi/emi/api/recipe/EmiRecipe;Z)"
                    + "Lappeng/integration/modules/emi/AbstractRecipeHandler$Result;",
            at = @At("RETURN"),
            require = 0)
    private static void ae2lt$restoreClosedLoopMode(
            AEBaseMenu menu,
            RecipeHolder<?> holder,
            EmiRecipe emiRecipe,
            boolean doTransfer,
            CallbackInfoReturnable<Object> cir) {
        if (ae2lt$restoreClosedLoopMode
                && menu instanceof TianshuPatternEncodingTermMenu tianshuMenu) {
            ae2lt$restoreClosedLoopMode = false;
            tianshuMenu.setTianshuMode(TianshuEncodingMode.CLOSED_LOOP);
        }
    }
}
