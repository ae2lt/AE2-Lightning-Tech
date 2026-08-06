package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/** Guards the two lowest-level mutation paths used by force and coordinate based movers. */
@Mixin(Entity.class)
public abstract class EntityPhaseMovementMixin {
    @Inject(
            method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void ae2lt$blockExternalPhaseFlightMove(MoverType moverType, Vec3 movement, CallbackInfo ci) {
        if ((Object) this instanceof Player player
                && PhaseFlightMovementGuard.blocksExternalForces(player)
                && !PhaseFlightMovementGuard.isSelfMovementAuthorized(player)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void ae2lt$blockExternalPhaseFlightForce(Vec3 movement, CallbackInfo ci) {
        if ((Object) this instanceof Player player
                && PhaseFlightMovementGuard.blocksExternalForces(player)
                && !PhaseFlightMovementGuard.isSelfMovementAuthorized(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "setPosRaw(DDD)V", at = @At("HEAD"), cancellable = true)
    private void ae2lt$blockExternalPhaseFlightTeleport(double x, double y, double z, CallbackInfo ci) {
        if (!((Object) this instanceof Player player)
                || !PhaseFlightMovementGuard.blocksExternalTeleports(player)
                || PhaseFlightMovementGuard.isSelfTeleportAuthorized(player)
                || PhaseFlightMovementGuard.isMovementPositionUpdate()) {
            return;
        }
        Vec3 current = player.position();
        if (Double.compare(current.x, x) != 0
                || Double.compare(current.y, y) != 0
                || Double.compare(current.z, z) != 0) {
            if (player instanceof ServerPlayer serverPlayer) {
                PhaseFlightMovementGuard.notifyBlockedTeleport(serverPlayer, new Vec3(x, y, z));
            }
            ci.cancel();
        }
    }
}
