package com.moakiee.ae2lt.mixin.recipeviewer.jei;

import com.moakiee.ae2lt.client.TianshuDirectUploadClient;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import mezz.jei.gui.recipes.RecipeTransferButtonController;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * JEI closes its recipe page after every successful transfer. Alt direct-upload keeps that page
 * open so multiple patterns can be encoded consecutively; an ambiguous target still falls back to
 * the normal terminal picker.
 */
@Mixin(value = RecipeTransferButtonController.class, remap = false)
public abstract class JeiRecipeTransferButtonControllerMixin {
    @Redirect(
            method = "onPress(Lmezz/jei/api/gui/inputs/IJeiUserInput;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/gui/recipes/RecipesGui;onClose()V"),
            require = 0)
    private void ae2lt$keepRecipePageForDirectUpload(RecipesGui recipesGui) {
        var menu = recipesGui.getParentContainerMenu();
        if (Screen.hasAltDown()
                && menu instanceof TianshuPatternEncodingTermMenu tianshuMenu
                && TianshuDirectUploadClient.holdRecipeScreen(tianshuMenu, recipesGui)) {
            return;
        }
        recipesGui.onClose();
    }
}
