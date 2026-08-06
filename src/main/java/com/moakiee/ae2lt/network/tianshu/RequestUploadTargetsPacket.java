package com.moakiee.ae2lt.network.tianshu;

import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestUploadTargetsPacket(int containerId) implements CustomPacketPayload {
    public static final Type<RequestUploadTargetsPacket> TYPE =
            new Type<>(NetworkInit.id("request_upload_targets"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestUploadTargetsPacket> STREAM_CODEC =
            StreamCodec.ofMember(RequestUploadTargetsPacket::write, RequestUploadTargetsPacket::decode);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
    }

    private static RequestUploadTargetsPacket decode(RegistryFriendlyByteBuf buf) {
        return new RequestUploadTargetsPacket(buf.readVarInt());
    }

    @Override public Type<RequestUploadTargetsPacket> type() { return TYPE; }

    public static void handle(RequestUploadTargetsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.sendUploadTargets(player);
            }
        });
    }
}
