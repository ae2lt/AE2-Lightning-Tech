package com.moakiee.ae2lt.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.celestweave.PhaseFlightControlRules;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;
import com.moakiee.ae2lt.celestweave.module.PhaseFlightSubmodule;

@Mixin(Player.class)
public abstract class PlayerPhaseFlightMixin implements PhaseFlightPlayerState.Access {
    @Shadow
    @Final
    private Abilities abilities;

    @Unique
    private boolean ae2lt$phaseFlightControlled;
    @Unique
    private boolean ae2lt$phaseFlying;
    @Unique
    private boolean ae2lt$phaseJumpHeld;
    @Unique
    private boolean ae2lt$phaseFlightLocked = true;

    @Override
    public boolean ae2lt$isPhaseFlightControlled() {
        return ae2lt$phaseFlightControlled;
    }

    @Override
    public void ae2lt$setPhaseFlightControlled(boolean controlled) {
        ae2lt$phaseFlightControlled = controlled;
    }

    @Override
    public boolean ae2lt$isPhaseFlying() {
        return ae2lt$phaseFlying;
    }

    @Override
    public void ae2lt$setPhaseFlying(boolean flying) {
        ae2lt$phaseFlying = flying;
    }

    @Override
    public boolean ae2lt$isPhaseJumpHeld() {
        return ae2lt$phaseJumpHeld;
    }

    @Override
    public void ae2lt$setPhaseJumpHeld(boolean jumpHeld) {
        ae2lt$phaseJumpHeld = jumpHeld;
    }

    @Override
    public boolean ae2lt$isPhaseFlightLocked() {
        return ae2lt$phaseFlightLocked;
    }

    @Override
    public void ae2lt$setPhaseFlightLocked(boolean locked) {
        ae2lt$phaseFlightLocked = locked;
    }

    @Override
    public boolean ae2lt$getVanillaFlying() {
        return abilities.flying;
    }

    @Override
    public void ae2lt$setVanillaFlying(boolean flying) {
        abilities.flying = flying;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ae2lt$syncPrivatePhaseFlightAbilities(CallbackInfo ci) {
        PhaseFlightPlayerState.maintainVanillaAbilities((Player) (Object) this);
    }

    @Inject(method = "getAbilities", at = @At("HEAD"))
    private void ae2lt$projectLockedPhaseFlying(CallbackInfoReturnable<Abilities> cir) {
        if (ae2lt$phaseFlightControlled && ae2lt$phaseFlightLocked) {
            abilities.flying = ae2lt$phaseFlying;
        }
    }

    @ModifyExpressionValue(
            method = {
                "tick",
                "isAffectedByFluids",
                "maybeBackOffFromEdge",
                "travel",
                "updateSwimming",
                "makeStuckInBlock",
                "getMovementEmission",
                "isSwimming",
                "isPushedByFluid",
                "getBlockSpeedFactor",
                "getFlyingSpeed"
            },
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z",
                    opcode = Opcodes.GETFIELD))
    private boolean ae2lt$readLockedPhaseFlying(boolean vanillaFlying) {
        return PhaseFlightPlayerState.readEffectiveFlying(
                (Player) (Object) this,
                vanillaFlying);
    }

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void ae2lt$keepPhaseFlightOutOfSwimmingPose(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        boolean phaseTraversal = PhaseFlightSubmodule.hasTransientPhaseState(player);
        boolean crouchChord = PhaseFlightControlRules.isCrouchChord(
                PhaseFlightPlayerState.isControlled(player),
                PhaseFlightPlayerState.isJumpHeld(player),
                player.isShiftKeyDown());
        boolean groundCrouch = PhaseFlightControlRules.exposeGroundCrouch(
                PhaseFlightPlayerState.isFlightLocked(player),
                PhaseFlightMovementGuard.isPhaseModeEnabled(player),
                PhaseFlightPlayerState.isFlying(player),
                player.onGround(),
                player.isShiftKeyDown());
        if (!phaseTraversal && !crouchChord && !groundCrouch) {
            return;
        }
        Pose pose;
        if (player.isFallFlying()) {
            pose = Pose.FALL_FLYING;
        } else if (crouchChord || groundCrouch) {
            pose = Pose.CROUCHING;
        } else {
            pose = Pose.STANDING;
        }
        player.setPose(pose);
        ci.cancel();
    }

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
        if (PhaseFlightSubmodule.hasTransientPhaseState(player)) {
            PhaseFlightSubmodule.applyTransientPhaseState(player);
        }
    }
}
