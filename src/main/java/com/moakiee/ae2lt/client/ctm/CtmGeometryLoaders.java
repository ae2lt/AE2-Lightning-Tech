package com.moakiee.ae2lt.client.ctm;

import com.moakiee.ae2lt.AE2LightningTech;

import net.minecraft.resources.ResourceLocation;
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
        // 1.20.1: register() takes a String id instead of a ResourceLocation.
        event.register(
                AE2LightningTech.MODID + ":connected_texture",
                new ConnectedTextureLoader());
    }
}
