package com.moakiee.ae2lt.client;

import java.util.List;

import com.moakiee.ae2lt.client.gui.FrequencyScreen;
import com.moakiee.ae2lt.network.SyncFrequencyDetailPacket;
import com.moakiee.ae2lt.network.SyncFrequencyListPacket;
import com.moakiee.ae2lt.network.UpdateFrequencyBasicPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public final class ClientNetworkPacketHandlers {

    private ClientNetworkPacketHandlers() {
    }

    public static void handleEasterEgg() {
        EasterEggOverlay.trigger();
    }

    public static void handleFrequencyResponse(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (minecraft.screen instanceof FrequencyScreen fs) {
            fs.showInlineError(message);
        } else {
            player.displayClientMessage(message, true);
        }
    }

    public static void handleFrequencyList(List<SyncFrequencyListPacket.FrequencyEntry> entries) {
        ClientFrequencyCache.updateFromSync(entries);
    }

    public static void handleFrequencyDetail(int frequencyId, byte syncType, CompoundTag data) {
        if (syncType == SyncFrequencyDetailPacket.TYPE_MEMBERS) {
            ClientFrequencyCache.updateMembers(frequencyId, data);
        } else if (syncType == SyncFrequencyDetailPacket.TYPE_CONNECTIONS) {
            ClientFrequencyCache.updateConnections(frequencyId, data);
        }
    }

    public static void handleFrequencyBasicUpdate(UpdateFrequencyBasicPacket packet) {
        if (packet.deleted()) {
            ClientFrequencyCache.removeFrequency(packet.frequencyId());
        } else {
            ClientFrequencyCache.upsertFrequency(
                    packet.frequencyId(),
                    packet.name(),
                    packet.color(),
                    packet.ownerUUID(),
                    packet.security());
        }
    }
}
