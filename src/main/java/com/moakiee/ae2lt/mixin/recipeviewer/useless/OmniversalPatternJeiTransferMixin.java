package com.moakiee.ae2lt.mixin.recipeviewer.useless;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.moakiee.ae2lt.integration.useless.UselessModCompat;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only Tianshu owns this transfer; all other terminals retain the upstream behavior. */
@Pseudo
@Mixin(targets = "com.sorrowmist.useless.compat.jei.OmniversalPatternJeiTransferHandler", remap = false)
public abstract class OmniversalPatternJeiTransferMixin {
    @Inject(method = "transferOmniversalRecipe", at = @At("HEAD"), cancellable = true, require = 1)
    private static void ae2lt$selectDedicatedOmniversalPage(
            PatternEncodingTermMenu menu, AlloyFurnaceRecipeCatalog.Entry entry,
            IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer,
            IRecipeTransferHandlerHelper helper, CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (!(menu instanceof TianshuPatternEncodingTermMenu tianshu)) return;
        if (doTransfer) {
            var pattern = UselessModCompat.encodeViewerRecipe(entry, player.level());
            if (pattern.isEmpty()) {
                cir.setReturnValue(helper.createUserErrorWithTooltip(
                        Component.translatable("ae2lt.tianshu.omniversal.invalid")));
                return;
            }
            tianshu.selectOmniversalPattern(pattern);
            if (Screen.hasAltDown()) tianshu.encodeAndUploadDirectly();
        }
        cir.setReturnValue(null);
    }
}
