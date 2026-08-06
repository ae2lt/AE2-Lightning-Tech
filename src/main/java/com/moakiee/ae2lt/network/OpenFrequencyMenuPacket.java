package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.api.frequency.FrequencyBindingHost;
import com.moakiee.ae2lt.api.frequency.FrequencyBindingMenuHost;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.item.TerminalCardAccess;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuHostLocator;

public record OpenFrequencyMenuPacket(boolean cardMode) implements CustomPacketPayload {
    public static final Type<OpenFrequencyMenuPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ae2lt", "open_frequency_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenFrequencyMenuPacket> STREAM_CODEC =
            StreamCodec.of(OpenFrequencyMenuPacket::encode, OpenFrequencyMenuPacket::decode);

    public static OpenFrequencyMenuPacket forBlock() {
        return new OpenFrequencyMenuPacket(false);
    }

    public static OpenFrequencyMenuPacket forCard() {
        return new OpenFrequencyMenuPacket(true);
    }

    private static void encode(FriendlyByteBuf buf, OpenFrequencyMenuPacket pkt) {
        buf.writeBoolean(pkt.cardMode);
    }

    private static OpenFrequencyMenuPacket decode(FriendlyByteBuf buf) {
        return new OpenFrequencyMenuPacket(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenFrequencyMenuPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            if (pkt.cardMode) {
                handleCardMode(player);
                return;
            }
            handleBlockMode(player);
        });
    }

    private static void handleBlockMode(ServerPlayer player) {
        if (!(player.containerMenu instanceof AEBaseMenu parentMenu)
                || !(parentMenu instanceof FrequencyBindingMenuHost)
                || !parentMenu.stillValid(player)) {
            reject(player);
            return;
        }

        MenuHostLocator parentLocator = parentMenu.getLocator();
        if (parentLocator == null) {
            reject(player);
            return;
        }

        FrequencyBindingHost bindingHost = parentLocator.locate(player, FrequencyBindingHost.class);
        if (bindingHost == null) {
            reject(player);
            return;
        }
        int freqId = bindingHost.getFrequencyId();
        if (freqId > 0) {
            var manager = WirelessFrequencyManager.get();
            var freq = manager == null ? null : manager.getFrequency(freqId);
            if (freq != null
                    && !freq.getPlayerAccess(player).canUse()
                    && freq.getSecurity() != FrequencySecurityLevel.ENCRYPTED) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.gui.error.no_access").withStyle(ChatFormatting.RED),
                        true);
                return;
            }
        }

        if (!MenuOpener.open(FrequencyMenu.TYPE, player, parentLocator)) {
            reject(player);
        }
    }

    private static void handleCardMode(ServerPlayer player) {
        if (!(player.containerMenu instanceof AEBaseMenu aeMenu)
                || !(aeMenu.getLocator() instanceof ItemMenuHostLocator locator)
                || !aeMenu.stillValid(player)) {
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

        if (!MenuOpener.open(FrequencyMenu.TYPE, player, locator)) {
            reject(player);
        }
    }

    private static void reject(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("ae2lt.gui.error.rejected").withStyle(ChatFormatting.RED),
                true);
    }
}
