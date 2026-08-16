package com.moakiee.ae2lt.client.core;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.core.veil.VeilCoreEffectShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

final class CoreEffectRenderTypes extends RenderType {
    private static final EffectShaders SHADERS = selectShaders();
    private static final RenderType TIANSHU =
            createEffectType("tianshu", SHADERS.tianshu());
    private static final RenderType MATRIX_CORE =
            createEffectType("matrix_core", SHADERS.matrix());
    private static final RenderType MATRIX_GLOW =
            createGlowEffectType("matrix_glow", SHADERS.matrix());
    private static final RenderType TIANSHU_SHADER_PACK_FALLBACK =
            createShaderPackFallbackType("tianshu", false, true, true);
    private static final RenderType MATRIX_CORE_SHADER_PACK_FALLBACK =
            createShaderPackFallbackType("matrix_core", false, true, true);
    private static final RenderType MATRIX_GLOW_SHADER_PACK_FALLBACK =
            createShaderPackFallbackType("matrix_glow", true, false, false);

    private CoreEffectRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                                  int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                                  Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    static RenderType tianshu(boolean shaderPackActive) {
        return shaderPackActive ? TIANSHU_SHADER_PACK_FALLBACK : TIANSHU;
    }

    static RenderType matrixCore(boolean shaderPackActive) {
        return shaderPackActive ? MATRIX_CORE_SHADER_PACK_FALLBACK : MATRIX_CORE;
    }

    static RenderType matrixGlow(boolean shaderPackActive) {
        return shaderPackActive ? MATRIX_GLOW_SHADER_PACK_FALLBACK : MATRIX_GLOW;
    }

    private static EffectShaders selectShaders() {
        if (CoreEffectBackend.useVeil()) {
            try {
                // Resolve both shaders as one unit so a partially compatible Veil API cannot
                // leave the two core effects using different backends.
                return new EffectShaders(
                        VeilCoreEffectShaders.tianshu(),
                        VeilCoreEffectShaders.matrix());
            } catch (RuntimeException | LinkageError exception) {
                CoreEffectBackend.disableVeil(exception);
            }
        }
        return new EffectShaders(CoreEffectShaders.tianshu(), CoreEffectShaders.matrix());
    }

    private record EffectShaders(
            RenderStateShard.ShaderStateShard tianshu,
            RenderStateShard.ShaderStateShard matrix) {
    }

    private static RenderType createEffectType(
            String name, RenderStateShard.ShaderStateShard shader) {
        var state = CompositeState.builder()
                .setShaderState(shader)
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

    private static RenderType createGlowEffectType(
            String name, RenderStateShard.ShaderStateShard shader) {
        var state = CompositeState.builder()
                .setShaderState(shader)
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

    private static RenderType createShaderPackFallbackType(
            String name, boolean additive, boolean writesDepth, boolean sortOnUpload) {
        // Iris accepts its replacement for this vanilla shader while rejecting unknown instances.
        var state = CompositeState.builder()
                .setShaderState(POSITION_COLOR_SHADER)
                .setTextureState(NO_TEXTURE)
                .setTransparencyState(additive ? ADDITIVE_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setWriteMaskState(writesDepth ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                .setCullState(NO_CULL)
                .setLightmapState(NO_LIGHTMAP)
                .createCompositeState(false);
        return create(
                AE2LightningTech.MODID + "_core_effect_" + name + "_shader_pack_fallback",
                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.TRIANGLES,
                262144,
                false,
                sortOnUpload,
                state);
    }
}
