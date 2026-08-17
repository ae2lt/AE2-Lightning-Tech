package com.moakiee.ae2lt.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.extensions.IPlayerExtension;

import com.moakiee.ae2lt.celestweave.PhaseWingFlight;

/** Adds Celestweave flight after every native or attribute-based flight provider has answered. */
@Mixin(value = IPlayerExtension.class, priority = Integer.MAX_VALUE, remap = false)
public interface PlayerMayFlyMixin {
    @ModifyReturnValue(method = "mayFly", at = @At("RETURN"))
    private boolean ae2lt$appendCelestweaveFlight(boolean original) {
        return original
                || ((Object) this instanceof Player player && PhaseWingFlight.canUse(player));
    }
}
