package com.moakiee.ae2lt.network;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;

/** Clientbound effective movement-protection settings for the active phase-lock module. */
public record PhaseLockProtectionSyncPacket(
        UUID armorId,
        boolean blockExternalForces) {
public static PhaseLockProtectionSyncPacket decode(FriendlyByteBuf buf) {
        return new PhaseLockProtectionSyncPacket(
                buf.readUUID(),
                buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(armorId);
        buf.writeBoolean(blockExternalForces);
    }

    public static void handle(PhaseLockProtectionSyncPacket payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> CelestweaveArmorState.setClientPhaseLockProtection(
                payload.armorId(),
                payload.blockExternalForces()));
    }
}
