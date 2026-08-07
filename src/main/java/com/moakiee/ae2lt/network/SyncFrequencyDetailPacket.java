package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.client.ClientNetworkPacketHandlers;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * S→C: send a single frequency's detail (members or connections) to the client.
 */
public record SyncFrequencyDetailPacket(int frequencyId, byte syncType, CompoundTag data) {

    public static final byte TYPE_MEMBERS = WirelessFrequency.NBT_MEMBERS_ONLY;
    public static final byte TYPE_CONNECTIONS = 10;

    public static void encode(SyncFrequencyDetailPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.frequencyId);
        buf.writeByte(pkt.syncType);
        buf.writeNbt(pkt.data);
    }

    public static SyncFrequencyDetailPacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        byte type = buf.readByte();
        CompoundTag tag = buf.readNbt();
        return new SyncFrequencyDetailPacket(id, type, tag == null ? new CompoundTag() : tag);
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
                NetworkInit.sendToPlayer(player, pkt);
            }
        }
    }

    public static void sendInitialMembersIfNeeded(ServerPlayer player, int frequencyId) {
        if (frequencyId <= 0) return;
        var manager = WirelessFrequencyManager.get();
        if (manager == null) return;
        WirelessFrequency freq = manager.getFrequency(frequencyId);
        if (freq == null) return;
        NetworkInit.sendToPlayer(player, forMembers(freq));
    }

    // ── Connections ──

    public static SyncFrequencyDetailPacket forConnections(int frequencyId, MinecraftServer server) {
        var manager = WirelessFrequencyManager.get();
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        if (manager != null && server != null) {
            for (var d : manager.getDevices(frequencyId)) {
                ServerLevel lvl = server.getLevel(d.dimension());
                boolean loaded = lvl != null
                        && lvl.getChunkSource().getChunkNow(d.pos().getX() >> 4, d.pos().getZ() >> 4) != null;
                String deviceName = d.deviceName();

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
        boolean hasReceiver = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof FrequencyMenu fm
                    && fm.getCurrentFrequencyId() == frequencyId) {
                hasReceiver = true;
                break;
            }
        }
        if (!hasReceiver) return;

        SyncFrequencyDetailPacket pkt = forConnections(frequencyId, server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof FrequencyMenu fm
                    && fm.getCurrentFrequencyId() == frequencyId) {
                NetworkInit.sendToPlayer(player, pkt);
            }
        }
    }

    public static void sendInitialConnectionsIfNeeded(ServerPlayer player, int frequencyId) {
        if (frequencyId <= 0) return;
        NetworkInit.sendToPlayer(player, forConnections(frequencyId, player.getServer()));
    }

    // ── Handler ──

    public static void handle(SyncFrequencyDetailPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientNetworkPacketHandlers.handleFrequencyDetail(
                        pkt.frequencyId(),
                        pkt.syncType(),
                        pkt.data())));
        ctx.setPacketHandled(true);
    }
}

