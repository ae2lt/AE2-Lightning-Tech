package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record CreateFrequencyPacket(
        int token,
        String name, int color,
        FrequencySecurityLevel security, String password
) {

    public static void encode(CreateFrequencyPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.token);
        buf.writeUtf(pkt.name, WirelessFrequency.MAX_NAME_LENGTH);
        buf.writeInt(pkt.color);
        buf.writeByte(pkt.security.getId());
        buf.writeUtf(pkt.password, WirelessFrequency.MAX_PASSWORD_LENGTH);
    }

    public static CreateFrequencyPacket decode(FriendlyByteBuf buf) {
        return new CreateFrequencyPacket(
                buf.readVarInt(),
                buf.readUtf(WirelessFrequency.MAX_NAME_LENGTH),
                buf.readInt(),
                FrequencySecurityLevel.fromId(buf.readByte()),
                buf.readUtf(WirelessFrequency.MAX_PASSWORD_LENGTH));
    }

    public static void handle(CreateFrequencyPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
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

            if (pkt.name.isBlank()) {
                NetworkInit.sendToPlayer(player, new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
                return;
            }
            // UI rule: "ENCRYPTED without a password is PRIVATE" — silently
            // downgrade instead of bouncing the request back with a
            // REQUIRE_PASSWORD error dialog. The client-side Create tab
            // doesn't force the user to enter a password before allowing
            // the ENCRYPTED option, so this fallback keeps the two layers
            // consistent.
            FrequencySecurityLevel effectiveSecurity = pkt.security;
            if (effectiveSecurity == FrequencySecurityLevel.ENCRYPTED && pkt.password.isBlank()) {
                effectiveSecurity = FrequencySecurityLevel.PRIVATE;
            }

            var freq = manager.createFrequency(player, pkt.name, pkt.color, effectiveSecurity, pkt.password);
            if (freq != null) {
                UpdateFrequencyBasicPacket.broadcastToOpenMenus(
                        player.getServer(), UpdateFrequencyBasicPacket.forFrequency(freq));
                // Push the member list to the creator immediately. The
                // generic {@link SyncFrequencyDetailPacket#broadcastMembersTo}
                // filters on "player.containerMenu's current freq id ==
                // this freq", but a newly-created frequency isn't yet
                // bound to any device — so without this explicit send
                // the creator's client frequency cache
                // never learns they're an OWNER member, and the client's
                // {@code needsPasswordUnlock} predicate misfires on the
                // first Select click (pops the password modal for the
                // creator of an ENCRYPTED freq they just set up).
                SyncFrequencyDetailPacket.sendInitialMembersIfNeeded(player, freq.getId());
            }
        });
        ctx.setPacketHandled(true);
    }
}

