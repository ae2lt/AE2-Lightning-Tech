package com.moakiee.ae2lt.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;

import com.moakiee.ae2lt.client.CelestweaveShieldFireVisuals;

@Mixin(Entity.class)
public abstract class EntityShieldFireVisualMixin {
    @Inject(method = "displayFireAnimation", at = @At("HEAD"), cancellable = true)
    private void ae2lt$hideShieldedPlayerFire(CallbackInfoReturnable<Boolean> cir) {
        if (CelestweaveShieldFireVisuals.shouldHideFire((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
