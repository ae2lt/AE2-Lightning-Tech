package com.moakiee.ae2lt.network;

import java.util.function.Supplier;
import java.util.UUID;

import com.moakiee.ae2lt.client.ClientNetworkPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

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
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientNetworkPacketHandlers.handlePhaseLockProtection(payload)));
        ctx.setPacketHandled(true);
    }
}
