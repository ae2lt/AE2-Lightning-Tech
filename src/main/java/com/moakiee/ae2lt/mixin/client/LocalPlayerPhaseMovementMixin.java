package com.moakiee.ae2lt.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/** Authorizes the vanilla space/shift vertical-flight impulse without authorizing world forces. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerPhaseMovementMixin {
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
