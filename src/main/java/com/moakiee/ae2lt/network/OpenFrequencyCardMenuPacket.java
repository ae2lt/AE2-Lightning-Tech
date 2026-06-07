package com.moakiee.ae2lt.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.menu.AEBaseMenu;
import appeng.menu.locator.ItemMenuHostLocator;

import com.moakiee.ae2lt.item.TerminalCardAccess;
import com.moakiee.ae2lt.menu.FrequencyMenu;

/**
 * C→S: opens the frequency management screen in "card mode" for the frequency
 * card installed in the wireless terminal the player currently has open. The
 * terminal is resolved from the player's open {@link AEBaseMenu} locator, so the
 * packet only needs the open menu's token for validation.
 */
public record OpenFrequencyCardMenuPacket(int token) implements CustomPacketPayload {

    public static final Type<OpenFrequencyCardMenuPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ae2lt", "open_frequency_card_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenFrequencyCardMenuPacket> STREAM_CODEC =
            StreamCodec.of(OpenFrequencyCardMenuPacket::encode, OpenFrequencyCardMenuPacket::decode);

    private static void encode(FriendlyByteBuf buf, OpenFrequencyCardMenuPacket pkt) {
        buf.writeVarInt(pkt.token);
    }

    private static OpenFrequencyCardMenuPacket decode(FriendlyByteBuf buf) {
        return new OpenFrequencyCardMenuPacket(buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenFrequencyCardMenuPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof AEBaseMenu aeMenu)
                    || aeMenu.containerId != pkt.token
                    || !(aeMenu.getLocator() instanceof ItemMenuHostLocator locator)) {
                reject(player);
                return;
            }

            ItemStack terminal = locator.locateItem(player);
            if (!TerminalCardAccess.hasCard(terminal)) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.frequency_card.terminal_no_card").withStyle(ChatFormatting.RED),
                        true);
                return;
            }

            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new FrequencyMenu(id, inv, locator),
                    Component.translatable("item.ae2lt.overloaded_frequency_card")
            ), buf -> FrequencyMenu.writeCardExtraData(buf, terminal));
        });
    }

    private static void reject(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("ae2lt.gui.error.rejected").withStyle(ChatFormatting.RED),
                true);
    }
}
