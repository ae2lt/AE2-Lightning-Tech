package com.moakiee.ae2lt.network;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.client.Minecraft;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;

public record FlightInertiaSyncPacket(
        UUID armorId,
        boolean inertiaEnabled,
        boolean phaseFlightActive,
        boolean phaseFlying,
        boolean phaseModeEnabled) {
public static FlightInertiaSyncPacket decode(FriendlyByteBuf buf) {
        return new FlightInertiaSyncPacket(
                buf.readUUID(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(armorId);
        buf.writeBoolean(inertiaEnabled);
        buf.writeBoolean(phaseFlightActive);
        buf.writeBoolean(phaseFlying);
        buf.writeBoolean(phaseModeEnabled);
    }

    public static void handle(FlightInertiaSyncPacket payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            CelestweaveArmorState.setClientFlightSettings(
                    payload.armorId(),
                    payload.inertiaEnabled(),
                    payload.phaseModeEnabled());
            var player = Minecraft.getInstance().player;
            if (payload.phaseFlightActive()) {
                PhaseFlightPlayerState.activate(player);
                PhaseFlightPlayerState.setFlying(player, payload.phaseFlying());
            } else {
                PhaseFlightPlayerState.endControl(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
