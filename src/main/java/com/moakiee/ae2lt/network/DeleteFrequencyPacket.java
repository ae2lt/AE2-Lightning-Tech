package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DeleteFrequencyPacket(int token, int frequencyId) implements CustomPacketPayload {

    public static final Type<DeleteFrequencyPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ae2lt", "delete_frequency"));

    public static final StreamCodec<FriendlyByteBuf, DeleteFrequencyPacket> STREAM_CODEC =
            StreamCodec.of(DeleteFrequencyPacket::encode, DeleteFrequencyPacket::decode);

    private static void encode(FriendlyByteBuf buf, DeleteFrequencyPacket pkt) {
        buf.writeVarInt(pkt.token);
        buf.writeInt(pkt.frequencyId);
    }

    private static DeleteFrequencyPacket decode(FriendlyByteBuf buf) {
        return new DeleteFrequencyPacket(buf.readVarInt(), buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DeleteFrequencyPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (FrequencyMenu.validateToken(player, pkt.token) == null) {
                PacketDistributor.sendToPlayer(player, new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
                return;
            }
            var manager = WirelessFrequencyManager.get();
            if (manager == null) return;

            WirelessFrequency freq = manager.getFrequency(pkt.frequencyId);
            if (freq == null) {
                PacketDistributor.sendToPlayer(player,
                        new FrequencyResponsePacket(FrequencyResponsePacket.INVALID_FREQUENCY));
                return;
            }

            // Multi-owner rule: any OWNER can delete the frequency —
            // we check isOwner() rather than the legacy single
            // ownerUUID equality, which would have gated deletion to
            // only the original creator.
            if (!freq.getPlayerAccess(player).isOwner()) {
                PacketDistributor.sendToPlayer(player,
                        new FrequencyResponsePacket(FrequencyResponsePacket.NO_PERMISSION));
                return;
            }

            manager.deleteFrequency(pkt.frequencyId, player.getServer());
            UpdateFrequencyBasicPacket.broadcastToOpenMenus(
                    player.getServer(), UpdateFrequencyBasicPacket.forDeletion(pkt.frequencyId));
        });
    }
}
