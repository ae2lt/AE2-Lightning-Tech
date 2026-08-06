package com.moakiee.ae2lt.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShieldHitFeedbackSuppressionPacket(int entityId) implements CustomPacketPayload {
    public static final Type<ShieldHitFeedbackSuppressionPacket> TYPE =
            new Type<>(NetworkInit.id("shield_hit_feedback_suppression"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShieldHitFeedbackSuppressionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    ShieldHitFeedbackSuppressionPacket::entityId,
                    ShieldHitFeedbackSuppressionPacket::new);

    @Override
    public Type<ShieldHitFeedbackSuppressionPacket> type() {
        return TYPE;
    }

    public static void handle(ShieldHitFeedbackSuppressionPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> ShieldHitFeedbackClientBridge.suppress(payload));
    }
}
