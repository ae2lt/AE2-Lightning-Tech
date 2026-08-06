package com.moakiee.ae2lt.client.ctm;

import com.moakiee.ae2lt.AE2LightningTech;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Registers the {@code ae2lt:connected_texture} geometry loader. */
@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CtmGeometryLoaders {

    private CtmGeometryLoaders() {
    }

    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(
                ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "connected_texture"),
                new ConnectedTextureLoader());
    }
}
