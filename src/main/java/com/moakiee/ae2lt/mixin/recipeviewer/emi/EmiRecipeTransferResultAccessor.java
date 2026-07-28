package com.moakiee.ae2lt.mixin.recipeviewer.emi;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * AE2 keeps its EMI transfer result package-private. This invoker exposes only the success bit so
 * the Alt shortcut cannot encode a recipe after EMI reports a failed or simulated transfer.
 */
@Mixin(targets = "appeng.integration.modules.emi.AbstractRecipeHandler$Result", remap = false)
public interface EmiRecipeTransferResultAccessor {
    @Invoker("canCraft")
    boolean ae2lt$canCraft();
}
