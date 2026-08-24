package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.network.SyncFrequencyListPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Keeps frequency names available for frequency-card tooltips outside the frequency menu. */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class FrequencyNameSyncHandler {
    private FrequencyNameSyncHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !(event.getEntity() instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(player, SyncFrequencyListPacket.fromServer());
        }
    }
}
