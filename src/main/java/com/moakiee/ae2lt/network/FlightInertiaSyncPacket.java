package com.moakiee.ae2lt.network;

import java.util.function.Supplier;
import java.util.UUID;

import com.moakiee.ae2lt.client.ClientNetworkPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record FlightInertiaSyncPacket(
        UUID armorId,
        boolean inertiaEnabled,
        boolean flightControlActive,
        boolean flying,
        boolean phaseModeEnabled,
        boolean flightLockEnabled) {
    public static FlightInertiaSyncPacket decode(FriendlyByteBuf buf) {
        return new FlightInertiaSyncPacket(
                buf.readUUID(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(armorId);
        buf.writeBoolean(inertiaEnabled);
        buf.writeBoolean(flightControlActive);
        buf.writeBoolean(flying);
        buf.writeBoolean(phaseModeEnabled);
        buf.writeBoolean(flightLockEnabled);
    }

    public static void handle(FlightInertiaSyncPacket payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientNetworkPacketHandlers.handleFlightInertia(payload)));
        ctx.setPacketHandled(true);
    }
}
