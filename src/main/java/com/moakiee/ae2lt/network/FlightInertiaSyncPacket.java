package com.moakiee.ae2lt.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;
import com.moakiee.ae2lt.celestweave.module.PhaseFlightMode;

public record FlightInertiaSyncPacket(
        UUID armorId,
        boolean inertiaEnabled,
        boolean flightControlActive,
        boolean flying,
        PhaseFlightMode phaseMode,
        boolean flightLockEnabled)
        implements CustomPacketPayload {

    public static final Type<FlightInertiaSyncPacket> TYPE =
            new Type<>(NetworkInit.id("flight_inertia_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlightInertiaSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(FlightInertiaSyncPacket::write, FlightInertiaSyncPacket::decode);

    @Override
    public Type<FlightInertiaSyncPacket> type() {
        return TYPE;
    }

    public static FlightInertiaSyncPacket decode(RegistryFriendlyByteBuf buf) {
        return new FlightInertiaSyncPacket(
                buf.readUUID(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readEnum(PhaseFlightMode.class),
                buf.readBoolean());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(armorId);
        buf.writeBoolean(inertiaEnabled);
        buf.writeBoolean(flightControlActive);
        buf.writeBoolean(flying);
        buf.writeEnum(phaseMode);
        buf.writeBoolean(flightLockEnabled);
    }

    public static void handle(FlightInertiaSyncPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            CelestweaveArmorState.setClientFlightSettings(
                    payload.armorId(),
                    payload.inertiaEnabled(),
                    payload.phaseMode());
            var player = context.player();
            if (payload.flightControlActive() || payload.flightLockEnabled()) {
                PhaseFlightPlayerState.activate(player);
                PhaseFlightPlayerState.setFlightLocked(player, payload.flightLockEnabled());
                PhaseFlightPlayerState.synchronizeFlying(player, payload.flying());
            } else {
                PhaseFlightPlayerState.endControl(player, payload.flying());
            }
        });
    }
}
