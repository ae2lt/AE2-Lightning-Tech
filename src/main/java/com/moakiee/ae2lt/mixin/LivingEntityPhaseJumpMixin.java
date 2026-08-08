package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/**
 * Authorizes only vanilla's two ground-jump impulses, without opening the rest of aiStep.
 * <p>1.20.1 note: both impulses (main + sprint) go through {@code setDeltaMovement(DDD)};
 * the {@code addDeltaMovement(Vec3)} sprint path of newer versions does not exist here.</p>
 */
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
}
