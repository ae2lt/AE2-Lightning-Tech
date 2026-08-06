package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
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

public record EditFrequencyPacket(
        int token,
        int frequencyId, String name, int color,
        FrequencySecurityLevel security, String password
) implements CustomPacketPayload {

    public static final Type<EditFrequencyPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ae2lt", "edit_frequency"));

    public static final StreamCodec<FriendlyByteBuf, EditFrequencyPacket> STREAM_CODEC =
            StreamCodec.of(EditFrequencyPacket::encode, EditFrequencyPacket::decode);

    private static void encode(FriendlyByteBuf buf, EditFrequencyPacket pkt) {
        buf.writeVarInt(pkt.token);
        buf.writeInt(pkt.frequencyId);
        buf.writeUtf(pkt.name, WirelessFrequency.MAX_NAME_LENGTH);
        buf.writeInt(pkt.color);
        buf.writeByte(pkt.security.getId());
        buf.writeUtf(pkt.password, WirelessFrequency.MAX_PASSWORD_LENGTH);
    }

    private static EditFrequencyPacket decode(FriendlyByteBuf buf) {
        return new EditFrequencyPacket(
                buf.readVarInt(),
                buf.readInt(),
                buf.readUtf(WirelessFrequency.MAX_NAME_LENGTH),
                buf.readInt(),
                FrequencySecurityLevel.fromId(buf.readByte()),
                buf.readUtf(WirelessFrequency.MAX_PASSWORD_LENGTH));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EditFrequencyPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            FrequencyMenu menu = FrequencyMenu.validateToken(player, pkt.token);
            if (menu == null || menu.getCurrentFrequencyId() != pkt.frequencyId) {
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

            var access = freq.getPlayerAccess(player);
            if (!access.isManager()) {
                PacketDistributor.sendToPlayer(player,
                        new FrequencyResponsePacket(FrequencyResponsePacket.NO_PERMISSION));
                return;
            }
            if (pkt.name.isBlank()) {
                PacketDistributor.sendToPlayer(player, new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
                return;
            }

            boolean securityChanged = freq.getSecurity() != pkt.security;
            // pkt.password is plaintext from the client; freq.getPassword()
            // is the stored SHA-256 hash. Hash the incoming plaintext with
            // the same id-salt before comparing — a raw .equals would
            // always report "changed" once the server-side hashing was
            // turned on.
            boolean passwordChanged = pkt.security == FrequencySecurityLevel.ENCRYPTED
                    && !pkt.password.isEmpty()
                    && !WirelessFrequency.hashPassword(pkt.password, freq.getId())
                            .equals(freq.getPassword());
            if ((securityChanged || passwordChanged) && !access.isOwner()) {
                PacketDistributor.sendToPlayer(player,
                        new FrequencyResponsePacket(FrequencyResponsePacket.NO_PERMISSION));
                return;
            }
            // UI rule: "ENCRYPTED without a password is PRIVATE". When the
            // request wants ENCRYPTED but there's no stored password AND
            // the client didn't supply a new one, silently downgrade
            // instead of bouncing with REQUIRE_PASSWORD. This is the
            // edit-time counterpart of the same rule in
            // {@link CreateFrequencyPacket}.
            FrequencySecurityLevel effectiveSecurity = pkt.security;
            if (effectiveSecurity == FrequencySecurityLevel.ENCRYPTED
                    && pkt.password.isBlank()
                    && freq.getPassword().isBlank()) {
                effectiveSecurity = FrequencySecurityLevel.PRIVATE;
            }

            freq.setName(pkt.name);
            freq.setColor(pkt.color);
            if (freq.getSecurity() != effectiveSecurity) {
                freq.setSecurity(effectiveSecurity);
            }
            if (effectiveSecurity != FrequencySecurityLevel.ENCRYPTED) {
                // clear stale password when leaving (or falling back from) ENCRYPTED
                freq.setPassword("");
            } else if (!pkt.password.isEmpty()) {
                freq.setPassword(pkt.password);
            }
            manager.markModified();
            UpdateFrequencyBasicPacket.broadcastToOpenMenus(
                    player.getServer(), UpdateFrequencyBasicPacket.forFrequency(freq));
        });
    }
}
