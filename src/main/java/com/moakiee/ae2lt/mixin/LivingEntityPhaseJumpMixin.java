package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/** Authorizes only direct vanilla travel and jump mutations. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPhaseJumpMixin {
    @WrapMethod(method = "travel")
    private void ae2lt$markVanillaTravelScope(Vec3 travelVector, Operation<Void> original) {
        if ((Object) this instanceof Player player) {
            PhaseFlightMovementGuard.runInVanillaTravelScope(
                    player,
                    () -> original.call(travelVector));
            return;
        }
        original.call(travelVector);
    }

    @WrapOperation(
            method = "travel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"))
    private void ae2lt$authorizeVanillaTravelInput(
            LivingEntity entity,
            float speed,
            Vec3 movement,
            Operation<Void> original) {
        runAsScopedVanillaTravelMovement(
                entity,
                () -> original.call(entity, speed, movement));
    }

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
        runAsScopedVanillaTravelMovement(
                entity,
                () -> original.call(entity, moverType, movement));
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
        runAsScopedVanillaTravelMovement(entity, () -> original.call(entity, movement));
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
        runAsScopedVanillaTravelMovement(entity, () -> original.call(entity, x, y, z));
    }

    @WrapOperation(
            method = "handleRelativeFrictionAndCalculateMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"))
    private void ae2lt$authorizeGroundTravelInput(
            LivingEntity entity,
            float speed,
            Vec3 movement,
            Operation<Void> original) {
        runAsScopedVanillaTravelMovement(
                entity,
                () -> original.call(entity, speed, movement));
    }

    @WrapOperation(
            method = "handleRelativeFrictionAndCalculateMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void ae2lt$authorizeGroundTravelVelocity(
            LivingEntity entity,
            Vec3 movement,
            Operation<Void> original) {
        runAsScopedVanillaTravelMovement(entity, () -> original.call(entity, movement));
    }

    @WrapOperation(
            method = "handleRelativeFrictionAndCalculateMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void ae2lt$authorizeGroundTravelMove(
            LivingEntity entity,
            MoverType moverType,
            Vec3 movement,
            Operation<Void> original) {
        runAsScopedVanillaTravelMovement(
                entity,
                () -> original.call(entity, moverType, movement));
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

    private static void runAsScopedVanillaTravelMovement(LivingEntity entity, Runnable movement) {
        if (entity instanceof Player player
                && PhaseFlightMovementGuard.isVanillaTravelScopeActive(player)) {
            PhaseFlightMovementGuard.runAsVanillaTravelMovement(player, movement);
        } else {
            movement.run();
        }
    }
}
