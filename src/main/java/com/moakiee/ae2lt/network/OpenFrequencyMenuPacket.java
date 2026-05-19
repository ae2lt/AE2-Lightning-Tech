package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.api.frequency.FrequencyBindingHost;
import com.moakiee.ae2lt.api.frequency.FrequencyBindingMenuHost;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record OpenFrequencyMenuPacket(int token, BlockPos blockPos) {

    public static void encode(OpenFrequencyMenuPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.token);
        buf.writeBlockPos(pkt.blockPos);
    }

    public static OpenFrequencyMenuPacket decode(FriendlyByteBuf buf) {
        return new OpenFrequencyMenuPacket(buf.readVarInt(), buf.readBlockPos());
    }

    public static void handle(OpenFrequencyMenuPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            if (!(player.containerMenu instanceof FrequencyBindingMenuHost menu)
                    || menu.getFrequencyBindingToken() != pkt.token
                    || !menu.getFrequencyBindingBlockPos().equals(pkt.blockPos)
                    || !((AbstractContainerMenu) menu).stillValid(player)) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.gui.error.rejected").withStyle(ChatFormatting.RED),
                        true);
                return;
            }

            var be = player.serverLevel().getBlockEntity(pkt.blockPos);
            if (!(be instanceof FrequencyBindingHost bindingHost)) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.gui.error.rejected").withStyle(ChatFormatting.RED),
                        true);
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

            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                    (id, inv, p) -> new FrequencyMenu(id, inv, be),
                    be.getBlockState().getBlock().getName()
            ), buf -> FrequencyMenu.writeExtraData(buf, be));
        });
        ctx.setPacketHandled(true);
    }
}

