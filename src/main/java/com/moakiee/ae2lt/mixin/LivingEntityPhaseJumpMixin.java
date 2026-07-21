package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/** Authorizes only vanilla's two ground-jump impulses, without opening the rest of aiStep. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPhaseJumpMixin {
    @WrapOperation(
            method = "jumpFromGround",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V"))
    private void ae2lt$authorizeVerticalJumpImpulse(
            LivingEntity entity,
            double x,
            double y,
            double z,
            Operation<Void> original) {
        if (entity instanceof Player player) {
            PhaseFlightMovementGuard.runAsSelfMovement(
                    player,
                    () -> original.call(entity, x, y, z));
            return;
        }
        original.call(entity, x, y, z);
    }

    @WrapOperation(
            method = "jumpFromGround",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;addDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void ae2lt$authorizeSprintingJumpImpulse(
            LivingEntity entity,
            Vec3 movement,
            Operation<Void> original) {
        if (entity instanceof Player player) {
            PhaseFlightMovementGuard.runAsSelfMovement(
                    player,
                    () -> original.call(entity, movement));
            return;
        }
        original.call(entity, movement);
    }
}
