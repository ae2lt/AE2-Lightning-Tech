package com.moakiee.ae2lt.mixin.compat;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.entity.player.Abilities;

import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;

/**
 * Draconic Evolution's guardian writes {@link Abilities#flying} directly on both logical sides.
 * Blocking only those two writes removes the one-tick false window without changing its behavior
 * for creative flight or players that are not actively phase-flying.
 * <p>
 * Remapping is disabled for the DE-owned class and method names, while the FIELD injection points
 * explicitly enable it so {@code Abilities.flying} is translated through the refmap to its SRG
 * name ({@code f_35935_}) in production jars.
 */
@Pseudo
@Mixin(
        targets = "com.brandon3055.draconicevolution.entity.guardian.control.ChargeUpPhase",
        remap = false)
public abstract class DraconicChargeUpPhaseFlightMixin {
    @Redirect(
            method = "serverTick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z",
                    opcode = Opcodes.PUTFIELD,
                    remap = true),
            require = 1,
            remap = false)
    private void ae2lt$preserveServerPhaseFlight(Abilities abilities, boolean flying) {
        writeFlyingUnlessPhaseLocked(abilities, flying);
    }

    @Redirect(
            method = "clientTick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z",
                    opcode = Opcodes.PUTFIELD,
                    remap = true),
            require = 1,
            remap = false)
    private void ae2lt$preserveClientPhaseFlight(Abilities abilities, boolean flying) {
        writeFlyingUnlessPhaseLocked(abilities, flying);
    }

    private static void writeFlyingUnlessPhaseLocked(Abilities abilities, boolean flying) {
        if (!flying && PhaseFlightPlayerState.rejectsExternalFlyingDisable(abilities)) {
            return;
        }
        abilities.flying = flying;
    }
}
