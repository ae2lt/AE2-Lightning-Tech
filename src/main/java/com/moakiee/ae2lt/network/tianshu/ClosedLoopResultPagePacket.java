package com.moakiee.ae2lt.network.tianshu;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopResultPage;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Bounded response containing at most one visible page of computed closed-loop results. */
public record ClosedLoopResultPagePacket(
        int containerId,
        ClosedLoopResultPage page) implements CustomPacketPayload {
    public static final Type<ClosedLoopResultPagePacket> TYPE =
            new Type<>(NetworkInit.id("closed_loop_result_page"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClosedLoopResultPagePacket>
            STREAM_CODEC = StreamCodec.ofMember(
                    ClosedLoopResultPagePacket::write, ClosedLoopResultPagePacket::decode);

    public ClosedLoopResultPagePacket {
        if (page == null) throw new IllegalArgumentException("missing closed-loop result page");
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(page.revision());
        buf.writeEnum(page.kind());
        buf.writeVarInt(page.offset());
        buf.writeVarInt(page.total());
        buf.writeVarInt(page.entries().size());
        for (var entry : page.entries()) {
            AEKey.STREAM_CODEC.encode(buf, entry.what());
            buf.writeVarLong(entry.amount());
        }
    }

    private static ClosedLoopResultPagePacket decode(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int revision = buf.readVarInt();
        var kind = buf.readEnum(ClosedLoopResultPage.Kind.class);
        int offset = buf.readVarInt();
        int total = buf.readVarInt();
        int size = buf.readVarInt();
        if (size < 0 || size > ClosedLoopResultPage.PAGE_SIZE) {
            throw new DecoderException("invalid closed-loop result page size: " + size);
        }
        var entries = new ArrayList<GenericStack>(size);
        for (int i = 0; i < size; i++) {
            var what = AEKey.STREAM_CODEC.decode(buf);
            long amount = buf.readVarLong();
            if (what == null || amount <= 0L) {
                throw new DecoderException("invalid closed-loop result entry");
            }
            entries.add(new GenericStack(what, amount));
        }
        try {
            return new ClosedLoopResultPagePacket(containerId,
                    new ClosedLoopResultPage(revision, kind, offset, total, entries));
        } catch (IllegalArgumentException exception) {
            throw new DecoderException("invalid closed-loop result page", exception);
        }
    }

    @Override
    public Type<ClosedLoopResultPagePacket> type() {
        return TYPE;
    }

    public static void handle(ClosedLoopResultPagePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.receiveClosedLoopResultPage(packet.page());
            }
        });
    }
}
