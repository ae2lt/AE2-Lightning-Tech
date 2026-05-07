package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.handling.IPayloadContext;

/**
 * S→C: send a single frequency's detail (members or connections) to the client.
 */
public record SyncFrequencyDetailPacket(int frequencyId, byte syncType, CompoundTag data)
        implements CustomPacketPayload {

    public static final byte TYPE_MEMBERS = WirelessFrequency.NBT_MEMBERS_ONLY;
    public static final byte TYPE_CONNECTIONS = 10;

    public static final Type<SyncFrequencyDetailPacket> TYPE =
            new Type<>(new ResourceLocation("ae2lt", "sync_frequency_detail"));

    public static final StreamCodec<FriendlyByteBuf, SyncFrequencyDetailPacket> STREAM_CODEC =
            StreamCodec.of(SyncFrequencyDetailPacket::encode, SyncFrequencyDetailPacket::decode);

    private static void encode(FriendlyByteBuf buf, SyncFrequencyDetailPacket pkt) {
        buf.writeInt(pkt.frequencyId);
        buf.writeByte(pkt.syncType);
        buf.writeNbt(pkt.data);
    }

    private static SyncFrequencyDetailPacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        byte type = buf.readByte();
        CompoundTag tag = buf.readNbt();
        return new SyncFrequencyDetailPacket(id, type, tag == null ? new CompoundTag() : tag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ── Members ──

    public static SyncFrequencyDetailPacket forMembers(WirelessFrequency freq) {
        CompoundTag tag = new CompoundTag();
        freq.writeToTag(tag, WirelessFrequency.NBT_MEMBERS_ONLY);
        return new SyncFrequencyDetailPacket(freq.getId(), TYPE_MEMBERS, tag);
    }

    public static void broadcastMembersTo(MinecraftServer server, int frequencyId) {
        var manager = WirelessFrequencyManager.get();
        if (manager == null) return;
        WirelessFrequency freq = manager.getFrequency(frequencyId);
        if (freq == null) return;

        SyncFrequencyDetailPacket pkt = forMembers(freq);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof FrequencyMenu fm
                    && fm.getCurrentFrequencyId() == frequencyId) {
                PacketDistributor.sendToPlayer(player, pkt);
            }
        }
    }

    public static void sendInitialMembersIfNeeded(ServerPlayer player, int frequencyId) {
        if (frequencyId <= 0) return;
        var manager = WirelessFrequencyManager.get();
        if (manager == null) return;
        WirelessFrequency freq = manager.getFrequency(frequencyId);
        if (freq == null) return;
        PacketDistributor.sendToPlayer(player, forMembers(freq));
    }

    // ── Connections ──

    public static SyncFrequencyDetailPacket forConnections(int frequencyId, MinecraftServer server) {
        var manager = WirelessFrequencyManager.get();
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        if (manager != null && server != null) {
            for (var d : manager.getDevices(frequencyId)) {
                ServerLevel lvl = server.getLevel(d.dimension());
                boolean loaded = lvl != null && lvl.isLoaded(d.pos());
                String deviceName = d.deviceName();
                if (loaded && lvl != null) {
                    deviceName = lvl.getBlockState(d.pos()).getBlock().getDescriptionId();
                }

                CompoundTag e = new CompoundTag();
                e.putString("dim", d.dimension().location().toString());
                e.putLong("pos", d.pos().asLong());
                e.putBoolean("controller", d.isController());
                e.putBoolean("advanced", d.advanced());
                e.putBoolean("loaded", loaded);
                e.putString("name", deviceName);
                list.add(e);
            }
        }
        tag.put("connections", list);
        return new SyncFrequencyDetailPacket(frequencyId, TYPE_CONNECTIONS, tag);
    }

    public static void broadcastConnectionsTo(MinecraftServer server, int frequencyId) {
        if (server == null || frequencyId <= 0) return;
        SyncFrequencyDetailPacket pkt = forConnections(frequencyId, server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof FrequencyMenu fm
                    && fm.getCurrentFrequencyId() == frequencyId) {
                PacketDistributor.sendToPlayer(player, pkt);
            }
        }
    }

    public static void sendInitialConnectionsIfNeeded(ServerPlayer player, int frequencyId) {
        if (frequencyId <= 0) return;
        PacketDistributor.sendToPlayer(player, forConnections(frequencyId, player.getServer()));
    }

    // ── Handler ──

    public static void handle(SyncFrequencyDetailPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (pkt.syncType == TYPE_MEMBERS) {
                com.moakiee.ae2lt.client.ClientFrequencyCache.updateMembers(pkt.frequencyId, pkt.data);
            } else if (pkt.syncType == TYPE_CONNECTIONS) {
                com.moakiee.ae2lt.client.ClientFrequencyCache.updateConnections(pkt.frequencyId, pkt.data);
            }
        });
    }
}

