package com.moakiee.ae2lt.network.tianshu;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.client.ClientNetworkPacketHandlers;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopResultPage;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Bounded response containing at most one visible page of computed closed-loop results. */
public record ClosedLoopResultPagePacket(int containerId, ClosedLoopResultPage page) {
    public ClosedLoopResultPagePacket {
        if (page == null) throw new IllegalArgumentException("missing closed-loop result page");
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(page.revision());
        buf.writeEnum(page.kind());
        buf.writeVarInt(page.offset());
        buf.writeVarInt(page.total());
        buf.writeVarInt(page.entries().size());
        for (var entry : page.entries()) {
            AEKey.writeKey(buf, entry.what());
            buf.writeVarLong(entry.amount());
        }
    }

    public static ClosedLoopResultPagePacket decode(FriendlyByteBuf buf) {
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
            var what = AEKey.readKey(buf);
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

    public static void handle(
            ClosedLoopResultPagePacket packet,
            Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientNetworkPacketHandlers.handleClosedLoopResultPage(packet)));
        ctx.setPacketHandled(true);
    }
}
