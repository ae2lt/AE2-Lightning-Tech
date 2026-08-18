package com.moakiee.ae2lt.client.ctm;

import com.moakiee.ae2lt.AE2LightningTech;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ModelEvent;

/** Registers the {@code ae2lt:connected_texture} geometry loader. */
@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CtmGeometryLoaders {

    private CtmGeometryLoaders() {
    }

    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        // 1.20.1: register(String) already prepends the mod id as the namespace,
        // so only the bare path must be passed (a full "modid:path" would be
        // wrapped again into "modid:modid:path" and rejected).
        event.register(
                "connected_texture",
                new ConnectedTextureLoader());
    }
}
