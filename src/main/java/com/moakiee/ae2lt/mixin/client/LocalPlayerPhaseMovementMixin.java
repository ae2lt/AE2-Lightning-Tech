package com.moakiee.ae2lt.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.celestweave.PhaseFlightControlRules;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.network.PhaseFlightInputPacket;

/** Authorizes the vanilla space/shift vertical-flight impulse without authorizing world forces. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerPhaseMovementMixin {
    @ModifyExpressionValue(
            method = "aiStep",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z",
                    opcode = Opcodes.GETFIELD))
    private boolean ae2lt$readEffectiveFlightState(boolean vanillaFlying) {
        return PhaseFlightPlayerState.readEffectiveFlying(
                (LocalPlayer) (Object) this,
                vanillaFlying);
    }

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0))
    private void ae2lt$rejectAlwaysFlyingOverride(Abilities abilities, boolean requestedFlying) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        abilities.flying = PhaseFlightPlayerState.isFlightLocked(player)
                ? PhaseFlightPlayerState.isFlying(player)
                : requestedFlying;
    }

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;onUpdateAbilities()V",
                    ordinal = 0))
    private void ae2lt$syncAlwaysFlyingUnlessLocked(LocalPlayer player) {
        if (!PhaseFlightPlayerState.isFlightLocked(player)) {
            player.onUpdateAbilities();
        }
    }

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 1))
    private void ae2lt$applyPhaseFlightInput(Abilities abilities, boolean requestedFlying) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!CelestweaveArmorState.isAnyClientFlightControlActive()
                && !PhaseFlightPlayerState.isFlightLocked(player)) {
            abilities.flying = requestedFlying;
            return;
        }
        PhaseFlightPlayerState.activate(player);
        if (PhaseFlightControlRules.rejectFlightToggle(
                PhaseFlightMovementGuard.isPhaseModeEnabled(player),
                PhaseFlightControlRules.intersectsWorldCollision(player),
                requestedFlying)) {
            requestedFlying = true;
        }
        if (requestedFlying && player.isFallFlying()) {
            player.stopFallFlying();
        }
        PhaseFlightPlayerState.applyFlightInput(player, requestedFlying);
        PacketDistributor.sendToServer(PhaseFlightInputPacket.flight(
                PhaseFlightPlayerState.isJumpHeld(player),
                requestedFlying));
    }

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;onUpdateAbilities()V",
                    ordinal = 1))
    private void ae2lt$useSinglePhaseFlightInputPath(LocalPlayer player) {
        if (!CelestweaveArmorState.isAnyClientFlightControlActive()
                && !PhaseFlightPlayerState.isFlightLocked(player)) {
            player.onUpdateAbilities();
        }
    }

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 2))
    private void ae2lt$preserveLockedFlightOnLanding(Abilities abilities, boolean requestedFlying) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (PhaseFlightControlRules.preserveFlightOnLanding(
                PhaseFlightPlayerState.isFlightLocked(player),
                PhaseFlightMovementGuard.isPhaseModeEnabled(player),
                PhaseFlightPlayerState.isFlying(player))) {
            abilities.flying = PhaseFlightPlayerState.isFlying(player);
        } else {
            abilities.flying = requestedFlying;
        }
    }

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;onUpdateAbilities()V",
                    ordinal = 2))
    private void ae2lt$syncLandingExitUnlessLocked(LocalPlayer player) {
        if (!PhaseFlightControlRules.preserveFlightOnLanding(
                PhaseFlightPlayerState.isFlightLocked(player),
                PhaseFlightMovementGuard.isPhaseModeEnabled(player),
                PhaseFlightPlayerState.isFlying(player))) {
            player.onUpdateAbilities();
        }
    }

    @Inject(method = "isCrouching", at = @At("HEAD"), cancellable = true)
    private void ae2lt$exposePhaseFlightCrouchChord(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        boolean crouchChord = PhaseFlightControlRules.isCrouchChord(
                CelestweaveArmorState.isAnyClientFlightControlActive(),
                PhaseFlightPlayerState.isJumpHeld(player),
                player.isShiftKeyDown());
        boolean groundCrouch = PhaseFlightControlRules.exposeGroundCrouch(
                PhaseFlightPlayerState.isFlightLocked(player),
                PhaseFlightMovementGuard.isPhaseModeEnabled(player),
                PhaseFlightPlayerState.isFlying(player),
                player.onGround(),
                player.isShiftKeyDown());
        if (crouchChord || groundCrouch) {
            cir.setReturnValue(true);
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
