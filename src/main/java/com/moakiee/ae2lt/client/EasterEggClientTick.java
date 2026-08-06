package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class EasterEggClientTick {
    private EasterEggClientTick() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        EasterEggOverlay.tick();
    }

    /**
     * Reset the overlay's static tick counter when the player disconnects so the
     * easter-egg image does not flash on the next world's HUD. This also avoids
     * keeping the counter pinned at a non-zero value across logical client
     * sessions, which would otherwise cause the {@code render(...)} layer to
     * keep doing work (allocating a {@code GuiGraphics} blit path each frame)
     * even though the player has already left.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        EasterEggOverlay.reset();
    }
}
