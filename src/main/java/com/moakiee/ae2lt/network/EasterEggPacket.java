package com.moakiee.ae2lt.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EasterEggPacket() implements CustomPacketPayload {
    public static final Type<EasterEggPacket> TYPE =
            new Type<>(NetworkInit.id("easter_egg"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EasterEggPacket> STREAM_CODEC =
            StreamCodec.ofMember(EasterEggPacket::write, EasterEggPacket::decode);

    @Override
    public Type<EasterEggPacket> type() {
        return TYPE;
    }

    public static EasterEggPacket decode(RegistryFriendlyByteBuf buf) {
        return new EasterEggPacket();
    }

    public void write(RegistryFriendlyByteBuf buf) {
    }

    public static void handle(EasterEggPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.moakiee.ae2lt.client.EasterEggOverlay.trigger();
        });
    }
}
