package com.moakiee.ae2lt.mixin.mekanism;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.world.entity.LivingEntity;

import mekanism.api.lasers.ILaserDissipation;

import com.moakiee.ae2lt.integration.mekanism.MekanismArmorIntegration;

/**
 * Captures the actual Joules present at Mekanism's native armor-dissipation step.
 *
 * <p>The original getter and all subsequent beam bookkeeping remain untouched. This hook only
 * mirrors energy into Celestweave when the queried capability is our own 100% dissipator.
 */
@Pseudo
@Mixin(targets = "mekanism.common.tile.laser.TileEntityBasicLaser", remap = false)
public abstract class TileEntityBasicLaserMixin {

    @WrapOperation(
            method = "onUpdateServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/api/lasers/ILaserDissipation;getDissipationPercent()D"),
            require = 1)
    private double ae2lt$chargeCelestweaveFromAbsorbedLaser(
            ILaserDissipation dissipation,
            Operation<Double> original,
            @Local(ordinal = 1) long remainingJoules,
            @Local LivingEntity target) {
        double percent = original.call(dissipation);
        MekanismArmorIntegration.absorbLaserEnergy(
                dissipation,
                target,
                remainingJoules,
                percent);
        return percent;
    }
}
