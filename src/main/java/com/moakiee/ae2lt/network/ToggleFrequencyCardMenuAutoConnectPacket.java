package com.moakiee.ae2lt.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.item.TerminalCardAccess;
import com.moakiee.ae2lt.menu.FrequencyMenu;

/**
 * C→S: toggles the auto-connect flag on the frequency card installed in the
 * wireless terminal whose card-mode {@link FrequencyMenu} the player has open.
 * The change is persisted back into the terminal stack's upgrade inventory.
 */
public record ToggleFrequencyCardMenuAutoConnectPacket(int token) implements CustomPacketPayload {

    public static final Type<ToggleFrequencyCardMenuAutoConnectPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ae2lt", "toggle_frequency_card_menu_auto_connect"));

    public static final StreamCodec<FriendlyByteBuf, ToggleFrequencyCardMenuAutoConnectPacket> STREAM_CODEC =
            StreamCodec.of(ToggleFrequencyCardMenuAutoConnectPacket::encode, ToggleFrequencyCardMenuAutoConnectPacket::decode);

    private static void encode(FriendlyByteBuf buf, ToggleFrequencyCardMenuAutoConnectPacket pkt) {
        buf.writeVarInt(pkt.token);
    }

    private static ToggleFrequencyCardMenuAutoConnectPacket decode(FriendlyByteBuf buf) {
        return new ToggleFrequencyCardMenuAutoConnectPacket(buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleFrequencyCardMenuAutoConnectPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            FrequencyMenu menu = FrequencyMenu.validateToken(player, pkt.token);
            if (menu == null || !menu.isCardMode()) {
                return;
            }
            TerminalCardAccess.updateCard(menu.resolveTerminalStack(), data -> data.toggleAutoConnect());
        });
    }
}
