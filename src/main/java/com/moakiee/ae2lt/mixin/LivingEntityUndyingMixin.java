package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorUndyingHandler;

@Mixin(LivingEntity.class)
public abstract class LivingEntityUndyingMixin {
    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void ae2lt$protectCelestweaveArmorFromKill(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof ServerPlayer player
                && CelestweaveArmorUndyingHandler.tryProtectForcedDeath(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void ae2lt$protectCelestweaveArmorFromSetHealth(float health, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (health <= 0.0F
                && entity instanceof ServerPlayer player
                && CelestweaveArmorUndyingHandler.tryProtectForcedDeath(player)) {
            ci.cancel();
        }
    }
}
