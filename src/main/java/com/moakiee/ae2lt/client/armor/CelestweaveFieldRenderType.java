package com.moakiee.ae2lt.client.armor;

import java.io.IOException;
import com.moakiee.ae2lt.AE2LightningTech;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CelestweaveFieldRenderType extends RenderType {
    private static ShaderInstance shader;
    private static final RenderType FIELD = create("ae2lt_celestweave_field",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 131072, false, true,
            CompositeState.builder().setShaderState(new ShaderStateShard(() -> shader))
                    .setTextureState(NO_TEXTURE).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).createCompositeState(false));

    private CelestweaveFieldRenderType(String name, VertexFormat format, VertexFormat.Mode mode,
            int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    @SubscribeEvent
    public static void registerShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "armor/celestweave_field"),
                DefaultVertexFormat.NEW_ENTITY), instance -> shader = instance);
    }

    public static RenderType field() { return FIELD; }
}
