package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/** Authorizes only direct vanilla travel and jump mutations. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPhaseJumpMixin {
    @WrapOperation(
            method = "travel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void ae2lt$authorizeVanillaTravelMove(
            LivingEntity entity,
            MoverType moverType,
            Vec3 movement,
            Operation<Void> original) {
        runAsVanillaTravelMovement(entity, () -> original.call(entity, moverType, movement));
    }

    @WrapOperation(
            method = "travel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void ae2lt$authorizeVanillaTravelVelocity(
            LivingEntity entity,
            Vec3 movement,
            Operation<Void> original) {
        runAsVanillaTravelMovement(entity, () -> original.call(entity, movement));
    }

    @WrapOperation(
            method = "travel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V"))
    private void ae2lt$authorizeVanillaTravelVelocityComponents(
            LivingEntity entity,
            double x,
            double y,
            double z,
            Operation<Void> original) {
        runAsVanillaTravelMovement(entity, () -> original.call(entity, x, y, z));
    }

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

    private static void runAsVanillaTravelMovement(LivingEntity entity, Runnable movement) {
        if (entity instanceof Player player) {
            PhaseFlightMovementGuard.runAsVanillaTravelMovement(player, movement);
        } else {
            movement.run();
        }
    }
}
