package com.moakiee.ae2lt.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;

public record CelestweaveSubmoduleActivePacket(UUID armorId, String submoduleId, boolean active)
        implements CustomPacketPayload {

    public static final Type<CelestweaveSubmoduleActivePacket> TYPE =
            new Type<>(NetworkInit.id("celestweave_submodule_active"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CelestweaveSubmoduleActivePacket> STREAM_CODEC =
            StreamCodec.ofMember(CelestweaveSubmoduleActivePacket::write, CelestweaveSubmoduleActivePacket::decode);

    @Override
    public Type<CelestweaveSubmoduleActivePacket> type() {
        return TYPE;
    }

    public static CelestweaveSubmoduleActivePacket decode(RegistryFriendlyByteBuf buf) {
        return new CelestweaveSubmoduleActivePacket(
                buf.readUUID(),
                buf.readUtf(128),
                buf.readBoolean());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(armorId);
        buf.writeUtf(submoduleId, 128);
        buf.writeBoolean(active);
    }

    public static void handle(CelestweaveSubmoduleActivePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> CelestweaveArmorState.markClientActive(
                payload.armorId(),
                payload.submoduleId(),
                payload.active()));
    }
}
