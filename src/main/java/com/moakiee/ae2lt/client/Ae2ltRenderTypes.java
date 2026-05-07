package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;

/**
 * Custom RenderType definitions for ae2lt overlays.
 * Extends RenderType to access protected RenderStateShard fields.
 */
public class Ae2ltRenderTypes extends RenderType {

    private static RenderType FACE_SEE_THROUGH;

    private Ae2ltRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
            int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
            Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    /**
     * A face (QUADS) render type that uses GREATER depth test,
     * so geometry behind opaque blocks becomes visible (see-through effect).
     */
    public static RenderType getFaceSeeThrough() {
        if (FACE_SEE_THROUGH == null) {
            FACE_SEE_THROUGH = create("ae2lt_face_see_through",
                    DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS, 65536, false, false,
                    CompositeState.builder()
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setTextureState(NO_TEXTURE)
                            .setLightmapState(NO_LIGHTMAP)
                            .setDepthTestState(GREATER_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .setShaderState(POSITION_COLOR_SHADER)
                            .createCompositeState(false));
        }
        return FACE_SEE_THROUGH;
    }
}
