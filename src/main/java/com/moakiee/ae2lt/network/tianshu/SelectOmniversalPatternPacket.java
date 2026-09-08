package com.moakiee.ae2lt.network.tianshu;

import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Carries the exact selected recipe; the server rebuilds and validates the native pattern. */
public record SelectOmniversalPatternPacket(int containerId, ItemStack pattern) implements CustomPacketPayload {
    public static final Type<SelectOmniversalPatternPacket> TYPE =
            new Type<>(NetworkInit.id("select_omniversal_pattern"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectOmniversalPatternPacket> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT, SelectOmniversalPatternPacket::containerId,
                    ItemStack.STREAM_CODEC, SelectOmniversalPatternPacket::pattern,
                    SelectOmniversalPatternPacket::new);

    @Override
    public Type<SelectOmniversalPatternPacket> type() {
        return TYPE;
    }

    public static void handle(SelectOmniversalPatternPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.selectOmniversalPattern(packet.pattern());
            }
        });
    }
}
