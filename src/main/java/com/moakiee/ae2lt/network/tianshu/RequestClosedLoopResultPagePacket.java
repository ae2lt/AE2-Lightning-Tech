package com.moakiee.ae2lt.network.tianshu;

import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopResultPage;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import io.netty.handler.codec.DecoderException;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Requests only the five computed-result rows currently visible in the closed-loop editor. */
public record RequestClosedLoopResultPagePacket(
        int containerId,
        ClosedLoopResultPage.Kind kind,
        int offset) {
    public RequestClosedLoopResultPagePacket {
        if (kind == null) throw new IllegalArgumentException("missing closed-loop result kind");
        if (offset < 0 || offset >= ClosedLoopResultPage.MAX_RESULTS) {
            throw new IllegalArgumentException("invalid closed-loop result offset: " + offset);
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeEnum(kind);
        buf.writeVarInt(offset);
    }

    public static RequestClosedLoopResultPagePacket decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        var kind = buf.readEnum(ClosedLoopResultPage.Kind.class);
        int offset = buf.readVarInt();
        if (offset < 0 || offset >= ClosedLoopResultPage.MAX_RESULTS) {
            throw new DecoderException("invalid closed-loop result offset: " + offset);
        }
        return new RequestClosedLoopResultPagePacket(containerId, kind, offset);
    }

    public static void handle(
            RequestClosedLoopResultPagePacket packet,
            Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null
                    && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.sendClosedLoopResultPage(player, packet.kind(), packet.offset());
            }
        });
        ctx.setPacketHandled(true);
    }
}
