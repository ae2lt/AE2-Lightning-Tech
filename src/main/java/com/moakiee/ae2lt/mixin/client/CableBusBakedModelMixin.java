package com.moakiee.ae2lt.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.block.model.BakedQuad;

import appeng.client.render.cablebus.CableBusBakedModel;
import appeng.client.render.cablebus.CableBusRenderState;

import com.moakiee.ae2lt.client.render.OverloadedCableRenderHelper;
import com.moakiee.ae2lt.client.render.OverloadedCableRenderStateAccess;

@Mixin(CableBusBakedModel.class)
public class CableBusBakedModelMixin {
    @Inject(method = "addCableQuads", at = @At("HEAD"), cancellable = true)
    private void ae2lt$renderOverloadedCable(CableBusRenderState renderState, List<BakedQuad> quadsOut,
            CallbackInfo ci) {
        if (renderState == null) {
            return;
        }
        if (!((OverloadedCableRenderStateAccess) renderState).ae2lt$isOverloadedCable()) {
            return;
        }

        OverloadedCableRenderHelper.addCableQuads(renderState, quadsOut);
        ci.cancel();
    }
}
