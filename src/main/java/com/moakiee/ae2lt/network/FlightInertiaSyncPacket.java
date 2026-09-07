package com.moakiee.ae2lt.network;

import java.util.function.Supplier;
import java.util.UUID;

import com.moakiee.ae2lt.client.ClientNetworkPacketHandlers;
import com.moakiee.ae2lt.celestweave.module.PhaseFlightMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.ByteTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record FlightInertiaSyncPacket(
        UUID armorId,
        boolean inertiaEnabled,
        boolean flightControlActive,
        boolean flying,
        PhaseFlightMode phaseMode,
        boolean flightLockEnabled) {
    public static FlightInertiaSyncPacket decode(FriendlyByteBuf buf) {
        return new FlightInertiaSyncPacket(
                buf.readUUID(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                PhaseFlightMode.fromTag(ByteTag.valueOf(buf.readByte())),
                buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(armorId);
        buf.writeBoolean(inertiaEnabled);
        buf.writeBoolean(flightControlActive);
        buf.writeBoolean(flying);
        // Preserve the Forge channel's legacy boolean values: 0=off, 1=all; 2 adds hover-only.
        buf.writeByte(phaseMode.toTag().getAsByte());
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
