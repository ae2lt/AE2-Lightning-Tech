package com.moakiee.ae2lt.network;

import java.util.ArrayList;
import java.util.List;

import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S→C packet: syncs the basic frequency list to the client. The current device
 * frequency id is auto-synced via the FrequencyMenu DataSlot, so this packet
 * carries only the registry contents.
 */
public record SyncFrequencyListPacket(List<FrequencyEntry> entries) implements CustomPacketPayload {

    public record FrequencyEntry(int id, String name, int color,
                                  java.util.UUID ownerUUID, FrequencySecurityLevel security) {}

    public static final Type<SyncFrequencyListPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ae2lt", "sync_frequency_list"));

    public static final StreamCodec<FriendlyByteBuf, SyncFrequencyListPacket> STREAM_CODEC =
            StreamCodec.of(SyncFrequencyListPacket::encode, SyncFrequencyListPacket::decode);

    private static void encode(FriendlyByteBuf buf, SyncFrequencyListPacket pkt) {
        buf.writeInt(pkt.entries.size());
        for (var e : pkt.entries) {
            buf.writeInt(e.id);
            buf.writeUtf(e.name, WirelessFrequency.MAX_NAME_LENGTH);
            buf.writeInt(e.color);
            buf.writeUUID(e.ownerUUID);
            buf.writeByte(e.security.getId());
        }
    }

    private static SyncFrequencyListPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<FrequencyEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new FrequencyEntry(
                    buf.readInt(),
                    buf.readUtf(WirelessFrequency.MAX_NAME_LENGTH),
                    buf.readInt(),
                    buf.readUUID(),
                    FrequencySecurityLevel.fromId(buf.readByte())));
        }
        return new SyncFrequencyListPacket(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static SyncFrequencyListPacket fromServer() {
        var manager = WirelessFrequencyManager.get();
        if (manager == null) return new SyncFrequencyListPacket(List.of());

        List<FrequencyEntry> entries = new ArrayList<>();
        for (var freq : manager.getAllFrequencies()) {
            entries.add(new FrequencyEntry(
                    freq.getId(), freq.getName(), freq.getColor(),
                    freq.getOwnerUUID(), freq.getSecurity()));
        }
        return new SyncFrequencyListPacket(entries);
    }

    public static void syncOpenMenus(MinecraftServer server) {
        SyncFrequencyListPacket pkt = fromServer();
        for (var player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof FrequencyMenu) {
                PacketDistributor.sendToPlayer(player, pkt);
            }
        }
    }

    public static void handle(SyncFrequencyListPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.moakiee.ae2lt.client.ClientFrequencyCache.updateFromSync(pkt.entries));
    }
}
