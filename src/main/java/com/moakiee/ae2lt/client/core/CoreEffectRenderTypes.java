package com.moakiee.ae2lt.client.core;

import com.moakiee.ae2lt.AE2LightningTech;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.api.client.render.VeilRenderBridge;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

final class CoreEffectRenderTypes extends RenderType {
    private static final ResourceLocation TIANSHU_SHADER =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "multiblock/tianshu_core");
    private static final ResourceLocation MATRIX_SHADER =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "multiblock/matrix_core");

    private static final RenderType TIANSHU = createEffectType("tianshu", TIANSHU_SHADER);
    private static final RenderType MATRIX_CORE = createEffectType("matrix_core", MATRIX_SHADER);
    private static final RenderType MATRIX_GLOW = createGlowEffectType("matrix_glow", MATRIX_SHADER);

    private CoreEffectRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                                  int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                                  Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    static RenderType tianshu() {
        return TIANSHU;
    }

    static RenderType matrixCore() {
        return MATRIX_CORE;
    }

    static RenderType matrixGlow() {
        return MATRIX_GLOW;
    }

    private static RenderType createEffectType(String name, ResourceLocation shader) {
        var state = CompositeState.builder()
                .setShaderState(VeilRenderBridge.shaderState(shader))
                .setTextureState(NO_TEXTURE)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setWriteMaskState(COLOR_DEPTH_WRITE)
                .setCullState(NO_CULL)
                .setLightmapState(NO_LIGHTMAP)
                .createCompositeState(false);
        return create(
                AE2LightningTech.MODID + "_core_effect_" + name,
                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.TRIANGLES,
                262144,
                false,
                true,
                state);
    }

    private static RenderType createGlowEffectType(String name, ResourceLocation shader) {
        var state = CompositeState.builder()
                .setShaderState(VeilRenderBridge.shaderState(shader))
                .setTextureState(NO_TEXTURE)
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE)
                .setCullState(NO_CULL)
                .setLightmapState(NO_LIGHTMAP)
                .createCompositeState(false);
        return create(
                AE2LightningTech.MODID + "_core_effect_" + name,
                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.TRIANGLES,
                262144,
                false,
                false,
                state);
    }
}
