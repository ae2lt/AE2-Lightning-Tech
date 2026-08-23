package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorDamageHandler;

/** Supplies the pre-processing damage stage that Forge 1.20.1 lacks. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityIncomingDamageMixin {
    @Inject(
            method = "hurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z"),
            cancellable = true)
    private void ae2lt$applyIncomingDamage(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir,
            @Local(argsOnly = true) LocalFloatRef mutableAmount,
            @Share("ae2lt$originalDamage") LocalFloatRef originalDamage) {
        originalDamage.set(amount);
        LivingEntity entity = (LivingEntity) (Object) this;
        var result = CelestweaveArmorDamageHandler.onIncomingDamage(entity, source, amount);
        if (result.canceled()) {
            cir.setReturnValue(false);
            return;
        }
        mutableAmount.set(result.amount());
    }

    @WrapOperation(
            method = "hurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
                    ordinal = 0))
    private void ae2lt$runCooldownDamageWithOriginalDamageScope(
            LivingEntity entity,
            DamageSource source,
            float amount,
            Operation<Void> original,
            @Share("ae2lt$originalDamage") LocalFloatRef originalDamage) {
        ae2lt$runWithOriginalDamageScope(entity, source, amount, original, originalDamage);
    }

    @WrapOperation(
            method = "hurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
                    ordinal = 1))
    private void ae2lt$runFreshDamageWithOriginalDamageScope(
            LivingEntity entity,
            DamageSource source,
            float amount,
            Operation<Void> original,
            @Share("ae2lt$originalDamage") LocalFloatRef originalDamage) {
        ae2lt$runWithOriginalDamageScope(entity, source, amount, original, originalDamage);
    }

    @Unique
    private static void ae2lt$runWithOriginalDamageScope(
            LivingEntity entity,
            DamageSource source,
            float amount,
            Operation<Void> original,
            LocalFloatRef originalDamage) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player)) {
            original.call(entity, source, amount);
            return;
        }
        int initialDepth = CelestweaveArmorDamageHandler.beginOriginalDamage(entity, originalDamage.get());
        try {
            original.call(entity, source, amount);
        } finally {
            CelestweaveArmorDamageHandler.finishOriginalDamage(entity, initialDepth);
        }
    }
}
