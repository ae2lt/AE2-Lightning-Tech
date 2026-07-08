package com.moakiee.ae2lt.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.Component;

import appeng.core.localization.Tooltips;

@Mixin(value = Tooltips.class, remap = false)
public final class TooltipsByteAmountMixin {
    private static final String INFINITE_TRANSLATION_KEY = "tooltip.ae2lt.infinite";
    private static final long BYTES_PER_PETABYTE = 1_125_899_906_842_624L;
    private static final long BYTES_PER_EXABYTE = 1_152_921_504_606_846_976L;

    private TooltipsByteAmountMixin() {
    }

    @Inject(method = "getByteAmount", at = @At("HEAD"), cancellable = true)
    private static void ae2lt$getHugeByteAmount(long amount, CallbackInfoReturnable<Tooltips.Amount> cir) {
        if (amount == Long.MAX_VALUE) {
            cir.setReturnValue(new Tooltips.Amount(Component.translatable(INFINITE_TRANSLATION_KEY).getString(), ""));
        } else if (amount >= BYTES_PER_EXABYTE) {
            cir.setReturnValue(new Tooltips.Amount(Tooltips.getAmount(amount, BYTES_PER_EXABYTE), "E"));
        } else if (amount >= BYTES_PER_PETABYTE) {
            cir.setReturnValue(new Tooltips.Amount(Tooltips.getAmount(amount, BYTES_PER_PETABYTE), "P"));
        }
    }
}
