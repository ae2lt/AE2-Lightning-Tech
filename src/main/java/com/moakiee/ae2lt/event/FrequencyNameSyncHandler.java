package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.network.NetworkInit;
import com.moakiee.ae2lt.network.SyncFrequencyListPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Keeps frequency names available for frequency-card tooltips outside the frequency menu. */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class FrequencyNameSyncHandler {
    private FrequencyNameSyncHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !(event.getEntity() instanceof FakePlayer)) {
            NetworkInit.sendToPlayer(player, SyncFrequencyListPacket.fromServer());
        }
    }
}
