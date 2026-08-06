package com.moakiee.ae2lt.network.tianshu;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UploadPatternToTargetPacket(int containerId, PatternContainerGroup group)
        implements CustomPacketPayload {
    public static final Type<UploadPatternToTargetPacket> TYPE =
            new Type<>(NetworkInit.id("upload_pattern_to_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UploadPatternToTargetPacket> STREAM_CODEC =
            StreamCodec.ofMember(UploadPatternToTargetPacket::write, UploadPatternToTargetPacket::decode);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        group.writeToPacket(buf);
    }

    private static UploadPatternToTargetPacket decode(RegistryFriendlyByteBuf buf) {
        return new UploadPatternToTargetPacket(
                buf.readVarInt(), PatternContainerGroup.readFromPacket(buf));
    }

    @Override public Type<UploadPatternToTargetPacket> type() { return TYPE; }

    public static void handle(UploadPatternToTargetPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.uploadTianshuPatternToTarget(player, packet.group());
            }
        });
    }
}
