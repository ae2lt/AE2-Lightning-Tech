package com.moakiee.ae2lt.network.hub;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import com.moakiee.ae2lt.menu.hub.DeviceHubHost;

/** Client → Server: request to open the DeviceHub UI. */
public record OpenDeviceHubPacket(int defaultTab) {
    public static OpenDeviceHubPacket decode(FriendlyByteBuf buf) {
        return new OpenDeviceHubPacket(buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(defaultTab);
    }

    public static void handle(OpenDeviceHubPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                DeviceHubHost.open(player, pkt.defaultTab());
            }
        });
        ctx.setPacketHandled(true);
    }
}
