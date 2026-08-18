package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EasterEggClientInit {
    private EasterEggClientInit() {
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAbove(
                VanillaGuiOverlay.CHAT_PANEL.id(),
                "easter_egg",
                EasterEggOverlay.INSTANCE);
    }
}

