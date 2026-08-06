package com.moakiee.ae2lt.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.celestweave.PhaseFlightControlRules;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;

/** Authorizes the vanilla space/shift vertical-flight impulse without authorizing world forces. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerPhaseMovementMixin {
    @Inject(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;onUpdateAbilities()V",
                    ordinal = 1,
                    shift = At.Shift.BEFORE))
    private void ae2lt$rejectPhaseFlightToggleInsideWall(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!CelestweaveArmorState.isAnyClientPhaseFlightActive()) {
            return;
        }
        PhaseFlightPlayerState.activate(player);
        boolean requestedFlying = player.getAbilities().flying;
        if (PhaseFlightControlRules.rejectFlightToggle(
                PhaseFlightMovementGuard.isPhaseModeEnabled(player),
                PhaseFlightControlRules.intersectsWorldCollision(player),
                requestedFlying)) {
            requestedFlying = true;
            player.getAbilities().flying = true;
        }
        PhaseFlightPlayerState.setFlying(player, requestedFlying);
    }

    @Inject(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;onUpdateAbilities()V",
                    ordinal = 2,
                    shift = At.Shift.BEFORE))
    private void ae2lt$keepPhaseFlightWhenTouchingGround(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (PhaseFlightControlRules.suppressLandingExit(
                PhaseFlightMovementGuard.isPhaseModeEnabled(player))) {
            player.getAbilities().flying = true;
            PhaseFlightPlayerState.setFlying(player, true);
        }
    }

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void ae2lt$authorizeVerticalFlightInput(LocalPlayer player, Vec3 movement) {
        PhaseFlightMovementGuard.runAsSelfMovement(
                player,
                () -> player.setDeltaMovement(movement));
    }
}
