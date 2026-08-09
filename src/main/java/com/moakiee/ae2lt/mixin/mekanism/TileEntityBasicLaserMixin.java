package com.moakiee.ae2lt.mixin.mekanism;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.world.entity.LivingEntity;

import mekanism.api.math.FloatingLong;

import com.moakiee.ae2lt.integration.mekanism.MekanismArmorIntegration;

/**
 * Captures the actual Joules present at Mekanism's native armor-dissipation step.
 *
 * <p>The hook wraps Mekanism's single armor-dissipation mutation and mirrors the actual
 * before/after energy difference into Celestweave. Targeting the mutation avoids relying on the
 * ordinal of a local variable in Mekanism's much larger entity-hit loop.
 */
@Pseudo
@Mixin(targets = "mekanism.common.tile.laser.TileEntityBasicLaser", remap = false)
public abstract class TileEntityBasicLaserMixin {

    @WrapOperation(
            method = "onUpdateServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/api/math/FloatingLong;timesEqual(Lmekanism/api/math/FloatingLong;)Lmekanism/api/math/FloatingLong;"),
            require = 1)
    private FloatingLong ae2lt$chargeCelestweaveFromAbsorbedLaser(
            FloatingLong remainingEnergy,
            FloatingLong retainedFraction,
            Operation<FloatingLong> original,
            @Local LivingEntity target) {
        FloatingLong energyBeforeDissipation = remainingEnergy.copy();
        FloatingLong retainedEnergy = original.call(remainingEnergy, retainedFraction);
        MekanismArmorIntegration.absorbLaserEnergy(
                target,
                energyBeforeDissipation.subtract(retainedEnergy));
        return retainedEnergy;
    }
}
