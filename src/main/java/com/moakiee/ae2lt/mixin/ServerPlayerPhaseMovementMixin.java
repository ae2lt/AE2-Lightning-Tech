package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/** Stops dimension transitions before the player is removed from the source level. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPhaseMovementMixin {
    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void ae2lt$blockExternalPhaseDimensionChange(
            DimensionTransition transition,
            CallbackInfoReturnable<Entity> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (PhaseFlightMovementGuard.blocksExternalTeleports(player)
                && !PhaseFlightMovementGuard.isSelfMovementAuthorized(player)) {
            PhaseFlightMovementGuard.notifyBlockedDimensionTeleport(
                    player,
                    transition.newLevel(),
                    transition.pos());
            cir.setReturnValue(null);
        }
    }
}
