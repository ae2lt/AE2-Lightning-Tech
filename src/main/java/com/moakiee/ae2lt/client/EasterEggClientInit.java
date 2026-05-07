package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RegisterGuiLayersEvent;
import net.minecraftforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EasterEggClientInit {
    private EasterEggClientInit() {
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.CHAT,
                new ResourceLocation(AE2LightningTech.MODID, "easter_egg"),
                EasterEggOverlay.INSTANCE);
    }
}

