package com.moakiee.ae2lt.mixin.thunderbolt.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import appeng.client.gui.me.crafting.CraftConfirmScreen;

@Mixin(value = CraftConfirmScreen.class, remap = false)
public abstract class CraftConfirmScreenCpuStatusMixin {
    @ModifyArg(
            method = "updateBeforeRender",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/core/localization/GuiText;text([Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;",
                    ordinal = 1),
            index = 0)
    private Object[] thunderbolt$formatInfiniteCpuStorage(Object[] args) {
        if (args.length > 0 && args[0] instanceof Long storage && storage == Long.MAX_VALUE) {
            args[0] = "∞";
        }
        return args;
    }
}
