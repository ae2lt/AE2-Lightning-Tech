package com.moakiee.ae2lt.network.hub;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.network.NetworkInit;

/** Server -> Client: sync string data for the hub (module name keys, device name, bound dim). */
public record DeviceHubSyncPacket(
        int containerId,
        String deviceName,
        String boundDim,
        List<String> moduleIds,
        List<String> moduleNameKeys,
        List<Integer> moduleCounts,
        List<Integer> moduleLoads
) implements CustomPacketPayload {

    public static final Type<DeviceHubSyncPacket> TYPE =
            new Type<>(NetworkInit.id("device_hub_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceHubSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(DeviceHubSyncPacket::write, DeviceHubSyncPacket::decode);

    @Override
    public Type<DeviceHubSyncPacket> type() {
        return TYPE;
    }

    public static DeviceHubSyncPacket decode(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        String deviceName = buf.readUtf(256);
        String boundDim = buf.readUtf(256);
        int count = buf.readVarInt();
        List<String> ids = new ArrayList<>(count);
        List<String> nameKeys = new ArrayList<>(count);
        List<Integer> counts = new ArrayList<>(count);
        List<Integer> loads = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(buf.readUtf(256));
            nameKeys.add(buf.readUtf(256));
            counts.add(buf.readVarInt());
            loads.add(buf.readVarInt());
        }
        return new DeviceHubSyncPacket(containerId, deviceName, boundDim, ids, nameKeys, counts, loads);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeUtf(deviceName, 256);
        buf.writeUtf(boundDim, 256);
        int count = Math.min(
                Math.min(moduleIds.size(), moduleNameKeys.size()),
                Math.min(moduleCounts.size(), moduleLoads.size()));
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeUtf(moduleIds.get(i), 256);
            buf.writeUtf(moduleNameKeys.get(i), 256);
            buf.writeVarInt(moduleCounts.get(i));
            buf.writeVarInt(moduleLoads.get(i));
        }
    }

    public static void handle(DeviceHubSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof DeviceHubMenu menu
                    && menu.containerId == pkt.containerId()) {
                menu.receiveSync(
                        pkt.deviceName(),
                        pkt.boundDim(),
                        pkt.moduleIds(),
                        pkt.moduleNameKeys(),
                        pkt.moduleCounts(),
                        pkt.moduleLoads());
            }
        });
    }
}
