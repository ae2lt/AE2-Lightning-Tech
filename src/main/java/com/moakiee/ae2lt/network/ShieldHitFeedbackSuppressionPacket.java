package com.moakiee.ae2lt.network;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

import net.minecraft.network.FriendlyByteBuf;

public record ShieldHitFeedbackSuppressionPacket(int entityId) {
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public static ShieldHitFeedbackSuppressionPacket decode(FriendlyByteBuf buf) {
        return new ShieldHitFeedbackSuppressionPacket(buf.readVarInt());
    }

    public static void handle(ShieldHitFeedbackSuppressionPacket payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ShieldHitFeedbackClientBridge.suppress(payload));
        ctx.setPacketHandled(true);
    }
}
