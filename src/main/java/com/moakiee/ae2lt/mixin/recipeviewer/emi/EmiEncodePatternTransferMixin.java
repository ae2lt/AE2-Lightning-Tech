package com.moakiee.ae2lt.mixin.recipeviewer.emi;

/*
 * Adapted from ExtendedAE Plus [ClientPlus] at revision
 * 07f8373c590c0c6d845f794e7c25090e5ef5703e. SPDX-License-Identifier: LGPL-3.0-only
 */

import appeng.integration.modules.emi.EmiEncodePatternHandler;
import appeng.integration.modules.emi.EmiStackHelper;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.api.stacks.GenericStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.integration.useless.UselessModCompat;
import com.moakiee.ae2lt.client.TianshuDirectUploadClient;
import com.moakiee.ae2lt.client.TianshuRecipeTransferContext;
import com.moakiee.ae2lt.client.TianshuUploadAliasRules;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
        // An actual transfer starts a new metadata generation. Clear before every early return so
        // recipes without a discoverable type/ID can never inherit the previous recipe's ID.
        TianshuRecipeTransferContext.clear(tianshuMenu);
        if (ae2lt$omniversalRecipe(emiRecipe) != null) return;
        if (tianshuMenu.tianshuMode == TianshuEncodingMode.CLOSED_LOOP && emiRecipe != null) {
            var output = EmiStackHelper.ofOutputs(emiRecipe).stream().findFirst().orElse(null);
            if (output != null && tianshuMenu.markClosedLoopPrimaryOutput(
                    appeng.api.stacks.GenericStack.wrapInItemStack(output))) {
                tianshuMenu.autoFillClosedLoop();
                // AE2's EMI result type is package-private, so let the normal transfer complete
                // and restore the Tianshu tab after its mode-change packet has been queued.
                ae2lt$restoreClosedLoopMode = true;
            }
            return;
        }
        tianshuMenu.resetProcessingEncoding();

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
                TianshuRecipeTransferContext.addDefaultAlias(
                        defaultAliases,
                        TianshuUploadAliasRules.namespaceGlob(
                                emiRecipe.getId().getNamespace()));
            }
            workstationAliases.forEach(alias ->
                    TianshuRecipeTransferContext.addDefaultAlias(defaultAliases, alias));
        }
        if (holder != null) {
            // A real recipe type is more stable and semantically precise than an EMI category.
            // Preserve category/workstation strings only as aliases for the right-hand field.
            TianshuRecipeTransferContext.captureVanillaRecipe(
                    tianshuMenu, holder, sourceKey, defaultAliases);
        } else {
            TianshuRecipeTransferContext.publish(
                    tianshuMenu, sourceKey, recipeId, defaultAliases);
        }
    }

    @Unique
    private static Object ae2lt$omniversalRecipe(EmiRecipe recipe) {
        if (recipe instanceof JemiRecipeAccessor wrapped
                && UselessModCompat.isViewerRecipe(wrapped.ae2lt$getRecipe())) {
            return wrapped.ae2lt$getRecipe();
        }
        return null;
    }

    @WrapOperation(
            method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;"
                    + "Lnet/minecraft/world/item/crafting/RecipeHolder;"
                    + "Ldev/emi/emi/api/recipe/EmiRecipe;Z)"
                    + "Lappeng/integration/modules/emi/AbstractRecipeHandler$Result;",
            at = @At(value = "INVOKE", target = "Lappeng/integration/modules/itemlists/EncodingHelper;"
                    + "encodeProcessingRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;"
                    + "Ljava/util/List;Ljava/util/List;)V"),
            require = 1)
    private void ae2lt$transferOmniversalRecipe(
            PatternEncodingTermMenu menu, List<List<GenericStack>> inputs, List<GenericStack> outputs,
            Operation<Void> original, PatternEncodingTermMenu targetMenu, RecipeHolder<?> holder,
            EmiRecipe recipe, boolean doTransfer) {
        var nativeRecipe = ae2lt$omniversalRecipe(recipe);
        if (menu instanceof TianshuPatternEncodingTermMenu tianshu && nativeRecipe != null) {
            var pattern = UselessModCompat.encodeViewerRecipe(nativeRecipe, menu.getPlayer().level());
            // A stale native recipe must enter the server's rejection path, never become a plain pattern.
            tianshu.selectOmniversalPattern(pattern.isEmpty() ? UselessModCompat.icon() : pattern);
            return;
        }
        original.call(menu, inputs, outputs);
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
            return;
        }
        if (doTransfer
                && Screen.hasAltDown()
                && menu instanceof TianshuPatternEncodingTermMenu tianshuMenu
                && tianshuMenu.tianshuMode != TianshuEncodingMode.CLOSED_LOOP
                && cir.getReturnValue() instanceof EmiRecipeTransferResultAccessor result
                && result.ae2lt$canCraft()) {
            tianshuMenu.encodeAndUploadDirectly();
            var currentScreen = Minecraft.getInstance().screen;
            if (currentScreen != null && currentScreen != EmiApi.getHandledScreen()) {
                TianshuDirectUploadClient.holdRecipeScreen(tianshuMenu, currentScreen);
            }
        }
    }
}
