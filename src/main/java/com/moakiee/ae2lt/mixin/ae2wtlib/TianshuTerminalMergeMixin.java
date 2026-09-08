package com.moakiee.ae2lt.mixin.ae2wtlib;

import com.moakiee.ae2lt.integration.ae2wtlib.TianshuTerminalMerge;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.wut.recipe.Common;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Common.class, remap = false)
public abstract class TianshuTerminalMergeMixin {
    @Inject(method = "mergeTerminal", at = @At("HEAD"), cancellable = true, require = 1)
    private static void ae2lt$mergeWithoutLoss(ItemStack target, ItemStack source, WTDefinition definition, CallbackInfoReturnable<ItemStack> cir) {
        if (TianshuTerminalMerge.applies(target, source, definition)) cir.setReturnValue(TianshuTerminalMerge.merge(target, source, definition));
    }
}
