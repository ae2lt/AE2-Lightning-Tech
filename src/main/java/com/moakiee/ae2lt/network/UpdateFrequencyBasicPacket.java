package com.moakiee.ae2lt.network;

import java.util.UUID;

import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S→C: incremental basic update for one frequency. Avoids re-sending the
 * entire list on every create/edit/delete.
 * When {@code deleted} is true, the remaining basic fields are placeholders
 * and the client removes this id from its cache.
 */
public record UpdateFrequencyBasicPacket(
        int frequencyId,
        boolean deleted,
        String name,
        int color,
        UUID ownerUUID,
        FrequencySecurityLevel security
) implements CustomPacketPayload {

    public static final Type<UpdateFrequencyBasicPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ae2lt", "update_frequency_basic"));

    public static final StreamCodec<FriendlyByteBuf, UpdateFrequencyBasicPacket> STREAM_CODEC =
            StreamCodec.of(UpdateFrequencyBasicPacket::encode, UpdateFrequencyBasicPacket::decode);

    private static final UUID ZERO_UUID = new UUID(0, 0);

    private static void encode(FriendlyByteBuf buf, UpdateFrequencyBasicPacket pkt) {
        buf.writeInt(pkt.frequencyId);
        buf.writeBoolean(pkt.deleted);
        if (!pkt.deleted) {
            buf.writeUtf(pkt.name, WirelessFrequency.MAX_NAME_LENGTH);
            buf.writeInt(pkt.color);
            buf.writeUUID(pkt.ownerUUID);
            buf.writeByte(pkt.security.getId());
        }
    }

    private static UpdateFrequencyBasicPacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        boolean deleted = buf.readBoolean();
        if (deleted) {
            return new UpdateFrequencyBasicPacket(id, true, "", 0, ZERO_UUID, FrequencySecurityLevel.PUBLIC);
        }
        return new UpdateFrequencyBasicPacket(
                id, false,
                buf.readUtf(WirelessFrequency.MAX_NAME_LENGTH),
                buf.readInt(),
                buf.readUUID(),
                FrequencySecurityLevel.fromId(buf.readByte()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static UpdateFrequencyBasicPacket forFrequency(WirelessFrequency freq) {
        return new UpdateFrequencyBasicPacket(
                freq.getId(), false,
                freq.getName(), freq.getColor(),
                freq.getOwnerUUID(), freq.getSecurity());
    }

    public static UpdateFrequencyBasicPacket forDeletion(int frequencyId) {
        return new UpdateFrequencyBasicPacket(frequencyId, true, "", 0, ZERO_UUID, FrequencySecurityLevel.PUBLIC);
    }

    /** Push to every player with any FrequencyMenu open (list is the same for everyone). */
    public static void broadcastToOpenMenus(MinecraftServer server, UpdateFrequencyBasicPacket pkt) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof FrequencyMenu) {
                PacketDistributor.sendToPlayer(player, pkt);
            }
        }
    }

    public static void handle(UpdateFrequencyBasicPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (pkt.deleted) {
                com.moakiee.ae2lt.client.ClientFrequencyCache.removeFrequency(pkt.frequencyId);
            } else {
                com.moakiee.ae2lt.client.ClientFrequencyCache.upsertFrequency(
                        pkt.frequencyId, pkt.name, pkt.color, pkt.ownerUUID, pkt.security);
            }
        });
    }
}
