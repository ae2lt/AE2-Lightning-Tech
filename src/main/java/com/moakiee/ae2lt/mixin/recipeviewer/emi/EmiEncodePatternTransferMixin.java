package com.moakiee.ae2lt.mixin.recipeviewer.emi;

/*
 * Adapted from ExtendedAE Plus [ClientPlus] at revision
 * 07f8373c590c0c6d845f794e7c25090e5ef5703e. SPDX-License-Identifier: LGPL-3.0-only
 */

import appeng.integration.modules.emi.EmiEncodePatternHandler;
import appeng.menu.AEBaseMenu;
import com.moakiee.ae2lt.client.TianshuRecipeTransferContext;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import java.util.TreeMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures EMI recipe/category identity without changing AE2's normal encoding lifecycle. */
@Mixin(value = EmiEncodePatternHandler.class, remap = false)
public abstract class EmiEncodePatternTransferMixin {
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
            CallbackInfoReturnable<?> cir) {
        if (!doTransfer || !(menu instanceof TianshuPatternEncodingTermMenu tianshuMenu)) return;
        if (holder != null && TianshuRecipeTransferContext.isSupportedCraftingRecipe(holder)) return;

        var queries = new TreeMap<Integer, Component>();
        String sourceKey = "";
        String recipeId = "";
        if (emiRecipe != null) {
            var category = emiRecipe.getCategory();
            if (category != null) {
                sourceKey = category.getId().toString();
                TianshuRecipeTransferContext.add(queries, 0, sourceKey);
                if (category.getName() != null && !category.getName().getString().isBlank()) {
                    queries.putIfAbsent(3, category.getName().copy());
                }
                int priority = 10;
                for (var workstation : EmiApi.getRecipeManager().getWorkstations(category).reversed()) {
                    for (var stack : workstation.getEmiStacks().reversed()) {
                        if (stack.getName() != null && !stack.getName().getString().isBlank()) {
                            queries.putIfAbsent(priority++, stack.getName().copy());
                        }
                    }
                }
            }
            if (emiRecipe.getId() != null) {
                recipeId = emiRecipe.getId().toString();
                TianshuRecipeTransferContext.add(
                        queries, 9,
                        TianshuRecipeTransferContext.firstPathSegment(emiRecipe.getId().getPath()));
            }
        }
        if (queries.isEmpty() && holder != null) {
            TianshuRecipeTransferContext.captureVanillaRecipe(tianshuMenu, holder);
        } else {
            TianshuRecipeTransferContext.publish(tianshuMenu, sourceKey, recipeId, queries);
        }
    }
}
