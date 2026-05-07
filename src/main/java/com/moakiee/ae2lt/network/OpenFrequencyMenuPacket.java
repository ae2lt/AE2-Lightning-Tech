package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.grid.FrequencyBindingHost;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.menu.FrequencyBindingMenu;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.handling.IPayloadContext;

public record OpenFrequencyMenuPacket(int token, BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<OpenFrequencyMenuPacket> TYPE =
            new Type<>(new ResourceLocation("ae2lt", "open_frequency_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenFrequencyMenuPacket> STREAM_CODEC =
            StreamCodec.of(OpenFrequencyMenuPacket::encode, OpenFrequencyMenuPacket::decode);

    private static void encode(FriendlyByteBuf buf, OpenFrequencyMenuPacket pkt) {
        buf.writeVarInt(pkt.token);
        buf.writeBlockPos(pkt.blockPos);
    }

    private static OpenFrequencyMenuPacket decode(FriendlyByteBuf buf) {
        return new OpenFrequencyMenuPacket(buf.readVarInt(), buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenFrequencyMenuPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            if (!(player.containerMenu instanceof FrequencyBindingMenu menu)
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

            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new FrequencyMenu(id, inv, be),
                    be.getBlockState().getBlock().getName()
            ), buf -> FrequencyMenu.writeExtraData(buf, be));
        });
    }
}

