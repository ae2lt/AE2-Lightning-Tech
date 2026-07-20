package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.celestweave.ArmorPhaseFlightRules;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.celestweave.module.PhaseFlightSubmodule;

@Mixin(Player.class)
public abstract class PlayerPhaseFlightMixin {
    @Inject(method = "travel", at = @At("HEAD"))
    private void ae2lt$beginPlayerAuthorizedTravel(Vec3 travelVector, CallbackInfo ci) {
        PhaseFlightMovementGuard.beginSelfMovement((Player) (Object) this);
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void ae2lt$endPlayerAuthorizedTravel(Vec3 travelVector, CallbackInfo ci) {
        PhaseFlightMovementGuard.endSelfMovement((Player) (Object) this);
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;updateIsUnderwater()Z",
                    shift = At.Shift.BEFORE))
    private void ae2lt$applyPhaseFlightPseudoSpectator(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (ArmorPhaseFlightRules.shouldApplyPseudoSpectatorState(
                PhaseFlightSubmodule.hasTransientPhaseState(player),
                true)) {
            PhaseFlightSubmodule.applyTransientPhaseState(player);
        }
    }
}
