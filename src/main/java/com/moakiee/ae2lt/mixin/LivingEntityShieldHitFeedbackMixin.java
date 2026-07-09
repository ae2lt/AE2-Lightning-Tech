package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorDamageHandler;

@Mixin(value = LivingEntity.class, priority = 900)
public abstract class LivingEntityShieldHitFeedbackMixin {
    @Shadow
    protected abstract void playHurtSound(DamageSource source);

    @Redirect(
            method = "hurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;broadcastDamageEvent(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V"),
            require = 0)
    private void ae2lt$skipShieldDamageEvent(Level level, Entity entity, DamageSource source) {
        if (entity instanceof LivingEntity living
                && CelestweaveArmorDamageHandler.shouldSuppressShieldHitFeedback(living)) {
            return;
        }
        level.broadcastDamageEvent(entity, source);
    }

    @WrapOperation(
            method = "hurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;markHurt()V"))
    private void ae2lt$skipShieldMarkHurt(LivingEntity entity, Operation<Void> original) {
        if (CelestweaveArmorDamageHandler.shouldSuppressShieldHitFeedback(entity)) {
            return;
        }
        original.call(entity);
    }

    @WrapOperation(
            method = "hurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;indicateDamage(DD)V"))
    private void ae2lt$skipShieldDamageIndication(
            LivingEntity entity,
            double x,
            double z,
            Operation<Void> original) {
        if (CelestweaveArmorDamageHandler.shouldSuppressShieldHitFeedback(entity)) {
            return;
        }
        original.call(entity, x, z);
    }

    @Redirect(
            method = "hurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;playHurtSound(Lnet/minecraft/world/damagesource/DamageSource;)V"),
            require = 0)
    private void ae2lt$skipShieldHurtSound(LivingEntity entity, DamageSource source) {
        if (CelestweaveArmorDamageHandler.shouldSuppressShieldHitFeedback(entity)) {
            return;
        }
        playHurtSound(source);
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void ae2lt$clearShieldHitFeedbackSuppression(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (CelestweaveArmorDamageHandler.shouldSuppressShieldHitFeedback(entity)) {
            CelestweaveArmorDamageHandler.suppressShieldHitFeedback(entity);
        }
        CelestweaveArmorDamageHandler.clearSuppressShieldHitFeedback(entity);
    }
}
