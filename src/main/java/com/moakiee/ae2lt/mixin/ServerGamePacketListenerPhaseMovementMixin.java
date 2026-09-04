package com.moakiee.ae2lt.mixin;

import java.util.Set;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
// 1.20.1 keeps ServerboundCustomPayloadPacket in the game package (1.21 moved it to common).
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;

/** Treats coordinates supplied by the player's own movement packet as authorized movement. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerPhaseMovementMixin {
    @Inject(method = "handlePlayerAbilities", at = @At("HEAD"), cancellable = true)
    private void ae2lt$rejectExternalLockedFlightUpdate(
            ServerboundPlayerAbilitiesPacket packet,
            CallbackInfo ci) {
        var player = ((ServerGamePacketListenerImpl) (Object) this).player;
        if (!PhaseFlightPlayerState.isFlightLocked(player)) {
            return;
        }
        // Locked hover input has its own packet. Vanilla ability packets are public-field
        // reconciliation and must not replace the private flight intent.
        player.getAbilities().flying = PhaseFlightPlayerState.isFlying(player);
        player.onUpdateAbilities();
        ci.cancel();
    }

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
                && !PhaseFlightMovementGuard.isSelfTeleportAuthorized(player)
                && !player.position().equals(target)) {
            PhaseFlightMovementGuard.notifyBlockedTeleport(player, target);
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "handleMovePlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isSingleplayerOwner()Z"))
    private boolean ae2lt$allowCreativeAndSpectatorMovement(
            ServerGamePacketListenerImpl listener,
            Operation<Boolean> original) {
        ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player;
        GameType gameType = player.gameMode.getGameModeForPlayer();
        return original.call(listener)
                || gameType == GameType.CREATIVE
                || gameType == GameType.SPECTATOR;
    }

    @WrapMethod(method = "handleMovePlayer")
    private void ae2lt$runPlayerAuthorizedMove(
            ServerboundMovePlayerPacket packet,
            Operation<Void> original) {
        var player = ((ServerGamePacketListenerImpl) (Object) this).player;
        PhaseFlightMovementGuard.beginMovementPacket(player);
        try {
            original.call(packet);
        } finally {
            PhaseFlightMovementGuard.endMovementPacket(player);
        }
    }

    @WrapMethod(method = "handleCustomPayload")
    private void ae2lt$runPlayerPayload(
            ServerboundCustomPayloadPacket packet,
            Operation<Void> original) {
        var player = ((ServerGamePacketListenerImpl) (Object) this).player;
        PhaseFlightMovementGuard.beginCustomPayload(player);
        try {
            original.call(packet);
        } finally {
            PhaseFlightMovementGuard.endCustomPayload(player);
        }
    }

    /** Vanilla simulates the player once, then restores the packet-owned position every tick. */
    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;absMoveTo(DDDFF)V"))
    private void ae2lt$authorizeVanillaTickPositionRestore(
            ServerPlayer player,
            double x,
            double y,
            double z,
            float yRot,
            float xRot,
            Operation<Void> original) {
        PhaseFlightMovementGuard.runAsSelfMovement(
                player,
                () -> original.call(player, x, y, z, yRot, xRot));
    }
}
