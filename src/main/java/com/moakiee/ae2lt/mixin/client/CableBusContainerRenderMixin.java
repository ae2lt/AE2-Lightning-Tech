package com.moakiee.ae2lt.mixin.client;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Direction;

import appeng.api.parts.IPart;
import appeng.client.render.cablebus.CableBusRenderState;
import appeng.parts.CableBusContainer;

import com.moakiee.ae2lt.client.render.OverloadedCableRenderStateAccess;
import com.moakiee.ae2lt.part.OverloadedCablePart;

@Mixin(CableBusContainer.class)
public abstract class CableBusContainerRenderMixin {
    @Shadow
    @Nullable
    public abstract IPart getPart(@Nullable Direction partLocation);

    @Inject(method = "getRenderState", at = @At("RETURN"))
    private void ae2lt$markOverloadedCable(CallbackInfoReturnable<CableBusRenderState> cir) {
        var renderState = cir.getReturnValue();
        if (renderState == null) {
            return;
        }
        if (getPart(null) instanceof OverloadedCablePart) {
            ((OverloadedCableRenderStateAccess) renderState).ae2lt$setOverloadedCable(true);
        }
    }
}
