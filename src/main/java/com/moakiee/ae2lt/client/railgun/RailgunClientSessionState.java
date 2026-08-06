package com.moakiee.ae2lt.client.railgun;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import com.moakiee.ae2lt.AE2LightningTech;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class RailgunClientSessionState {
    private RailgunClientSessionState() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        clear();
    }

    private static void clear() {
        RailgunBeamInput.reset();
        RailgunBeamRenderClient.reset();
        RailgunArcRenderer.clear();
        RailgunShockwaveRenderer.clear();
        RailgunCameraShake.clear();
    }
}
