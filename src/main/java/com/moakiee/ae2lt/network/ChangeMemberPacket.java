package com.moakiee.ae2lt.network;

import java.util.UUID;

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

public record ChangeMemberPacket(
        int token,
        int frequencyId, UUID targetUUID, byte operationType
) implements CustomPacketPayload {

    public static final Type<ChangeMemberPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ae2lt", "change_member"));

    public static final StreamCodec<FriendlyByteBuf, ChangeMemberPacket> STREAM_CODEC =
            StreamCodec.of(ChangeMemberPacket::encode, ChangeMemberPacket::decode);

    private static void encode(FriendlyByteBuf buf, ChangeMemberPacket pkt) {
        buf.writeVarInt(pkt.token);
        buf.writeInt(pkt.frequencyId);
        buf.writeUUID(pkt.targetUUID);
        buf.writeByte(pkt.operationType);
    }

    private static ChangeMemberPacket decode(FriendlyByteBuf buf) {
        return new ChangeMemberPacket(buf.readVarInt(), buf.readInt(), buf.readUUID(), buf.readByte());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChangeMemberPacket pkt, IPayloadContext ctx) {
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

            int result = freq.changeMembership(player, pkt.targetUUID, pkt.operationType);
            if (result == WirelessFrequency.RESPONSE_SUCCESS) {
                manager.markModified();
                SyncFrequencyDetailPacket.broadcastMembersTo(player.getServer(), pkt.frequencyId);
                return;
            }

            int responseCode = switch (result) {
                case WirelessFrequency.RESPONSE_NO_PERMISSION -> FrequencyResponsePacket.NO_PERMISSION;
                default -> FrequencyResponsePacket.REJECTED;
            };
            PacketDistributor.sendToPlayer(player, new FrequencyResponsePacket(responseCode));
        });
    }
}
