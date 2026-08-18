package com.moakiee.ae2lt.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;

import com.moakiee.ae2lt.client.ShieldHitFeedbackClientState;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerShieldHitFeedbackMixin {
    @Inject(method = "hurtTo", at = @At("RETURN"))
    private void ae2lt$clearShieldHealthFeedback(float health, CallbackInfo ci) {
        ShieldHitFeedbackClientState.clearAfterHealthSync((LocalPlayer) (Object) this);
    }
}
