package com.moakiee.ae2lt.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;

import com.moakiee.ae2lt.client.ShieldHitFeedbackClientState;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerShieldHitFeedbackMixin {
    @WrapOperation(
            method = "handleHurtAnimation",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;animateHurt(F)V"))
    private void ae2lt$skipShieldHurtAnimation(Entity entity, float yaw, Operation<Void> original) {
        if (ShieldHitFeedbackClientState.suppressHurtAnimation(entity)) {
            return;
        }
        original.call(entity, yaw);
    }
}
