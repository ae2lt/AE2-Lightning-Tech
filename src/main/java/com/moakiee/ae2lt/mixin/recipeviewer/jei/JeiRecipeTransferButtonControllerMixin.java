package com.moakiee.ae2lt.mixin.recipeviewer.jei;

import com.moakiee.ae2lt.client.JeiRecipeTransferMetadata;
import com.moakiee.ae2lt.client.TianshuDirectUploadClient;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.gui.recipes.RecipeTransferButtonController;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JEI closes its recipe page after every successful transfer. Alt direct-upload keeps that page
 * open so multiple patterns can be encoded consecutively; an ambiguous target still falls back to
 * the normal terminal picker.
 */
@Mixin(value = RecipeTransferButtonController.class, remap = false)
public abstract class JeiRecipeTransferButtonControllerMixin {
    @Accessor("recipeLayout")
    protected abstract IRecipeLayoutDrawable<?> ae2lt$getRecipeLayout();

    @Accessor("recipesGui")
    protected abstract RecipesGui ae2lt$getRecipesGui();

    @Inject(
            method = "onPress(Lmezz/jei/api/gui/inputs/IJeiUserInput;)Z",
            at = @At("HEAD"),
            require = 0)
    private void ae2lt$beginRecipeTransferMetadata(
            IJeiUserInput input, CallbackInfoReturnable<Boolean> cir) {
        JeiRecipeTransferMetadata.clear();
        if (input == null || input.isSimulate()) return;
        var menu = ae2lt$getRecipesGui().getParentContainerMenu();
        if (menu instanceof TianshuPatternEncodingTermMenu tianshuMenu) {
            JeiRecipeTransferMetadata.begin(tianshuMenu, ae2lt$getRecipeLayout());
        }
    }

    @Inject(
            method = "onPress(Lmezz/jei/api/gui/inputs/IJeiUserInput;)Z",
            at = @At("RETURN"),
            require = 0)
    private void ae2lt$clearRecipeTransferMetadata(
            IJeiUserInput input, CallbackInfoReturnable<Boolean> cir) {
        JeiRecipeTransferMetadata.clear();
    }

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
