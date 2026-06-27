package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorUndyingHandler;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerUndyingMixin {
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void ae2lt$protectCelestweaveArmorFromDie(DamageSource source, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (CelestweaveArmorUndyingHandler.tryProtectForcedDeath(player)) {
            ci.cancel();
        }
    }
}
