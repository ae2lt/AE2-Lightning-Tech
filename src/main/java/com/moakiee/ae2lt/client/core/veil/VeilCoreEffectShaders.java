package com.moakiee.ae2lt.client.core.veil;

import com.moakiee.ae2lt.AE2LightningTech;
import foundry.veil.api.client.render.VeilRenderBridge;
import java.lang.reflect.Modifier;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.ResourceLocation;

/**
 * Isolates every Veil class reference from the native fallback path.
 *
 * <p>This class must only be loaded after checking that the {@code veil} mod is present.</p>
 */
public final class VeilCoreEffectShaders {
    private static final ResourceLocation TIANSHU_SHADER =
            new ResourceLocation(AE2LightningTech.MODID, "multiblock/tianshu_core");
    private static final ResourceLocation MATRIX_SHADER =
            new ResourceLocation(AE2LightningTech.MODID, "multiblock/matrix_core");

    private VeilCoreEffectShaders() {
    }

    public static boolean isApiCompatible() {
        try {
            var shaderState = VeilRenderBridge.class.getMethod(
                    "shaderState", ResourceLocation.class);
            return Modifier.isStatic(shaderState.getModifiers())
                    && RenderStateShard.ShaderStateShard.class.isAssignableFrom(
                            shaderState.getReturnType());
        } catch (ReflectiveOperationException | SecurityException exception) {
            return false;
        }
    }

    public static RenderStateShard.ShaderStateShard tianshu() {
        return VeilRenderBridge.shaderState(TIANSHU_SHADER);
    }

    public static RenderStateShard.ShaderStateShard matrix() {
        return VeilRenderBridge.shaderState(MATRIX_SHADER);
    }
}
