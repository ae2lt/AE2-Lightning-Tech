package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.event.LightningItemTransformationHandler;
import com.moakiee.ae2lt.event.NaturalLightningTransformationHandler;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBolt.class)
public abstract class LightningBoltMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void ae2lt$handleLightningTick(CallbackInfo ci) {
        LightningBolt lightningBolt = (LightningBolt) (Object) this;
        NaturalLightningTransformationHandler.handleLightningTick(lightningBolt);
        LightningItemTransformationHandler.handleLightningTick(lightningBolt);
    }
}
