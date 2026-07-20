package com.moakiee.ae2lt.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/** Treats coordinates supplied by the player's own movement packet as authorized movement. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerPhaseMovementMixin {
    @Inject(
            method = "teleport(DDDFFLjava/util/Set;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void ae2lt$blockExternalPhaseTeleportPacket(
            double x,
            double y,
            double z,
            float yRot,
            float xRot,
            Set<RelativeMovement> relativeMovements,
            CallbackInfo ci) {
        var player = ((ServerGamePacketListenerImpl) (Object) this).player;
        Vec3 target = new Vec3(
                relativeMovements.contains(RelativeMovement.X) ? player.getX() + x : x,
                relativeMovements.contains(RelativeMovement.Y) ? player.getY() + y : y,
                relativeMovements.contains(RelativeMovement.Z) ? player.getZ() + z : z);
        if (PhaseFlightMovementGuard.blocksExternalTeleports(player)
                && !PhaseFlightMovementGuard.isSelfMovementAuthorized(player)
                && !player.position().equals(target)) {
            PhaseFlightMovementGuard.notifyBlockedTeleport(player, target);
            ci.cancel();
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void ae2lt$beginPlayerAuthorizedMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        PhaseFlightMovementGuard.beginSelfMovement(
                ((ServerGamePacketListenerImpl) (Object) this).player);
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void ae2lt$endPlayerAuthorizedMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        PhaseFlightMovementGuard.endSelfMovement(
                ((ServerGamePacketListenerImpl) (Object) this).player);
    }
}
