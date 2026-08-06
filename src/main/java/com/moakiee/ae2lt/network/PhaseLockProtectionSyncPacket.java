package com.moakiee.ae2lt.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;

/** Clientbound effective movement-protection settings for the active phase-lock module. */
public record PhaseLockProtectionSyncPacket(
        UUID armorId,
        boolean blockExternalForces)
        implements CustomPacketPayload {

    public static final Type<PhaseLockProtectionSyncPacket> TYPE =
            new Type<>(NetworkInit.id("phase_lock_protection_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhaseLockProtectionSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(PhaseLockProtectionSyncPacket::write, PhaseLockProtectionSyncPacket::decode);

    @Override
    public Type<PhaseLockProtectionSyncPacket> type() {
        return TYPE;
    }

    public static PhaseLockProtectionSyncPacket decode(RegistryFriendlyByteBuf buf) {
        return new PhaseLockProtectionSyncPacket(
                buf.readUUID(),
                buf.readBoolean());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(armorId);
        buf.writeBoolean(blockExternalForces);
    }

    public static void handle(PhaseLockProtectionSyncPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> CelestweaveArmorState.setClientPhaseLockProtection(
                payload.armorId(),
                payload.blockExternalForces()));
    }
}
