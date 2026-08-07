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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;

public record OpenFrequencyMenuPacket(boolean cardMode) {

    public static OpenFrequencyMenuPacket forBlock() {
        return new OpenFrequencyMenuPacket(false);
    }

    public static OpenFrequencyMenuPacket forCard() {
        return new OpenFrequencyMenuPacket(true);
    }

    public static void encode(OpenFrequencyMenuPacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.cardMode);
    }

    public static OpenFrequencyMenuPacket decode(FriendlyByteBuf buf) {
        return new OpenFrequencyMenuPacket(buf.readBoolean());
    }

    public static void handle(OpenFrequencyMenuPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            if (pkt.cardMode) {
                handleCardMode(player);
                return;
            }
            handleBlockMode(player);
        });
        ctx.setPacketHandled(true);
    }

    private static void handleBlockMode(ServerPlayer player) {
        if (!(player.containerMenu instanceof AEBaseMenu parentMenu)
                || !(parentMenu instanceof FrequencyBindingMenuHost)
                || !parentMenu.stillValid(player)) {
            reject(player);
            return;
        }

        MenuLocator parentLocator = parentMenu.getLocator();
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
        // 15.x has no ItemMenuHostLocator type: probe the parent menu's locator
        // for an item-backed host to decide whether this is a terminal card menu.
        if (!(player.containerMenu instanceof AEBaseMenu aeMenu)
                || aeMenu.getLocator() == null
                || !aeMenu.stillValid(player)) {
            reject(player);
            return;
        }
        MenuLocator locator = aeMenu.getLocator();
        ItemMenuHost terminalHost = locator.locate(player, ItemMenuHost.class);
        if (terminalHost == null) {
            reject(player);
            return;
        }

        ItemStack terminal = terminalHost.getItemStack();
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
