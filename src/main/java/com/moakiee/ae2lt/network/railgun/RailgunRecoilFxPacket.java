package com.moakiee.ae2lt.network.railgun;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.network.NetworkInit;

/** Server to client: apply visual recoil after a charged shot. */
public record RailgunRecoilFxPacket(float pitchUp, int tierOrdinal) implements CustomPacketPayload {

    public static final Type<RailgunRecoilFxPacket> TYPE =
            new Type<>(NetworkInit.id("railgun_recoil"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunRecoilFxPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, RailgunRecoilFxPacket::pitchUp,
                    ByteBufCodecs.VAR_INT, RailgunRecoilFxPacket::tierOrdinal,
                    RailgunRecoilFxPacket::new);

    @Override
    public Type<RailgunRecoilFxPacket> type() {
        return TYPE;
    }

    public static void handle(RailgunRecoilFxPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> RailgunClientBridge.recoil(p));
    }
}
