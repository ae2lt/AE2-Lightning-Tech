package com.moakiee.ae2lt.mixin;

import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.pathing.ControllerState;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.pathfinding.ControllerValidator;

import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;

/**
 * Forces {@link ControllerState#CONTROLLER_CONFLICT} when the grid contains
 * both overloaded and vanilla controllers.  Overloaded controllers must
 * operate on a dedicated network without vanilla controllers.
 */
@Mixin(ControllerValidator.class)
public abstract class ControllerValidatorMixin {

    @Inject(method = "calculateState", at = @At("RETURN"), cancellable = true)
    private static void ae2lt$forceMixedConflict(
            Collection<ControllerBlockEntity> controllers,
            CallbackInfoReturnable<ControllerState> cir) {

        if (cir.getReturnValue() == ControllerState.NO_CONTROLLER) return;

        boolean hasOverloaded = false;
        boolean hasVanilla = false;
        for (var c : controllers) {
            if (c instanceof OverloadedControllerBlockEntity) {
                hasOverloaded = true;
            } else {
                hasVanilla = true;
            }
            if (hasOverloaded && hasVanilla) {
                cir.setReturnValue(ControllerState.CONTROLLER_CONFLICT);
                return;
            }
        }
    }
}
