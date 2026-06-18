package com.moakiee.ae2lt.network.hub;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.menu.hub.DeviceHubHost;
import com.moakiee.ae2lt.network.NetworkInit;

/** Client → Server: request to open the DeviceHub UI. */
public record OpenDeviceHubPacket(int defaultTab) implements CustomPacketPayload {

    public static final Type<OpenDeviceHubPacket> TYPE =
            new Type<>(NetworkInit.id("open_device_hub"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDeviceHubPacket> STREAM_CODEC =
            StreamCodec.ofMember(OpenDeviceHubPacket::write, OpenDeviceHubPacket::decode);

    @Override
    public Type<OpenDeviceHubPacket> type() {
        return TYPE;
    }

    public static OpenDeviceHubPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenDeviceHubPacket(buf.readVarInt());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(defaultTab);
    }

    public static void handle(OpenDeviceHubPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                DeviceHubHost.open(player, pkt.defaultTab());
            }
        });
    }
}
