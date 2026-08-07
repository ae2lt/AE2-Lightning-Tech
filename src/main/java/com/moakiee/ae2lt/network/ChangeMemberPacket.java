package com.moakiee.ae2lt.network;

import java.util.UUID;
import java.util.function.Supplier;

import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record ChangeMemberPacket(
        int token,
        int frequencyId, UUID targetUUID, byte operationType
) {

    public static void encode(ChangeMemberPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.token);
        buf.writeInt(pkt.frequencyId);
        buf.writeUUID(pkt.targetUUID);
        buf.writeByte(pkt.operationType);
    }

    public static ChangeMemberPacket decode(FriendlyByteBuf buf) {
        return new ChangeMemberPacket(buf.readVarInt(), buf.readInt(), buf.readUUID(), buf.readByte());
    }

    public static void handle(ChangeMemberPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (FrequencyMenu.validateToken(player, pkt.token) == null) {
                NetworkInit.sendToPlayer(player, new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
                return;
            }
            var manager = WirelessFrequencyManager.get();
            if (manager == null) return;

            WirelessFrequency freq = manager.getFrequency(pkt.frequencyId);
            if (freq == null) {
                NetworkInit.sendToPlayer(player,
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
            NetworkInit.sendToPlayer(player, new FrequencyResponsePacket(responseCode));
        });
        ctx.setPacketHandled(true);
    }
}

