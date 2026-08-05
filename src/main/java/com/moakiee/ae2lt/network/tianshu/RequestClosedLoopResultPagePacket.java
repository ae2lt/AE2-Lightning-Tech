package com.moakiee.ae2lt.network.tianshu;

import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopResultPage;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Requests only the five computed-result rows currently visible in the closed-loop editor. */
public record RequestClosedLoopResultPagePacket(
        int containerId,
        ClosedLoopResultPage.Kind kind,
        int offset) implements CustomPacketPayload {
    public static final Type<RequestClosedLoopResultPagePacket> TYPE =
            new Type<>(NetworkInit.id("request_closed_loop_result_page"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestClosedLoopResultPagePacket>
            STREAM_CODEC = StreamCodec.ofMember(
                    RequestClosedLoopResultPagePacket::write,
                    RequestClosedLoopResultPagePacket::decode);

    public RequestClosedLoopResultPagePacket {
        if (kind == null) throw new IllegalArgumentException("missing closed-loop result kind");
        if (offset < 0 || offset >= ClosedLoopResultPage.MAX_RESULTS) {
            throw new IllegalArgumentException("invalid closed-loop result offset: " + offset);
        }
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeEnum(kind);
        buf.writeVarInt(offset);
    }

    private static RequestClosedLoopResultPagePacket decode(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        var kind = buf.readEnum(ClosedLoopResultPage.Kind.class);
        int offset = buf.readVarInt();
        if (offset < 0 || offset >= ClosedLoopResultPage.MAX_RESULTS) {
            throw new DecoderException("invalid closed-loop result offset: " + offset);
        }
        return new RequestClosedLoopResultPagePacket(containerId, kind, offset);
    }

    @Override
    public Type<RequestClosedLoopResultPagePacket> type() {
        return TYPE;
    }

    public static void handle(RequestClosedLoopResultPagePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.sendClosedLoopResultPage(player, packet.kind(), packet.offset());
            }
        });
    }
}
