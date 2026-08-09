package com.moakiee.ae2lt.client.core;

import com.moakiee.ae2lt.AE2LightningTech;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.function.Supplier;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CoreEffectShaders {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TIANSHU_SHADER =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "multiblock/tianshu_core");
    private static final ResourceLocation MATRIX_SHADER =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "multiblock/matrix_core");

    private static final ShaderTracker TIANSHU = new ShaderTracker();
    private static final ShaderTracker MATRIX = new ShaderTracker();

    private CoreEffectShaders() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        if (CoreEffectBackend.useVeil()) {
            LOGGER.info("Compatible Veil detected; preparing native core-effect shaders as fallback");
        } else {
            LOGGER.info("Veil not detected or incompatible; using the native core-effect shader backend");
        }

        registerShader(event, TIANSHU_SHADER, TIANSHU);
        registerShader(event, MATRIX_SHADER, MATRIX);
    }

    static RenderStateShard.ShaderStateShard tianshu() {
        return TIANSHU.shard;
    }

    static RenderStateShard.ShaderStateShard matrix() {
        return MATRIX.shard;
    }

    private static void registerShader(RegisterShadersEvent event,
                                       ResourceLocation location,
                                       ShaderTracker tracker) throws IOException {
        event.registerShader(
                new TimedShaderInstance(
                        event.getResourceProvider(),
                        location,
                        DefaultVertexFormat.POSITION_COLOR_NORMAL),
                tracker::setInstance);
    }

    private static final class ShaderTracker implements Supplier<ShaderInstance> {
        private final RenderStateShard.ShaderStateShard shard =
                new RenderStateShard.ShaderStateShard(this);
        private ShaderInstance instance;

        private void setInstance(ShaderInstance instance) {
            this.instance = instance;
        }

        @Override
        public ShaderInstance get() {
            return instance;
        }
    }

    /**
     * Keeps the old VeilRenderTime behavior without depending on Veil:
     * wall-clock seconds, wrapped once per hour to preserve float precision.
     */
    private static final class TimedShaderInstance extends ShaderInstance {
        private static final long TIME_WRAP_MILLIS = 3_600_000L;

        private final Uniform effectTime;

        private TimedShaderInstance(ResourceProvider resources,
                                    ResourceLocation location,
                                    VertexFormat vertexFormat) throws IOException {
            super(resources, location, vertexFormat);
            this.effectTime = getUniform("EffectTime");
        }

        @Override
        public void apply() {
            if (effectTime != null) {
                effectTime.set((System.currentTimeMillis() % TIME_WRAP_MILLIS) / 1000.0F);
            }
            // ShaderInstance uploads dirty uniforms inside apply(), so update the value first.
            super.apply();
        }
    }
}
