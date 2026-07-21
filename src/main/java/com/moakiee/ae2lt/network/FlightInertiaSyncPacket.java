package com.moakiee.ae2lt.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;

public record FlightInertiaSyncPacket(
        UUID armorId,
        boolean inertiaEnabled,
        boolean phaseFlightActive,
        boolean phaseFlying,
        boolean phaseModeEnabled)
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
                buf.readBoolean());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(armorId);
        buf.writeBoolean(inertiaEnabled);
        buf.writeBoolean(phaseFlightActive);
        buf.writeBoolean(phaseFlying);
        buf.writeBoolean(phaseModeEnabled);
    }

    public static void handle(FlightInertiaSyncPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            CelestweaveArmorState.setClientFlightSettings(
                    payload.armorId(),
                    payload.inertiaEnabled(),
                    payload.phaseModeEnabled());
            var player = context.player();
            if (payload.phaseFlightActive()) {
                PhaseFlightPlayerState.activate(player);
                PhaseFlightPlayerState.setFlying(player, payload.phaseFlying());
            } else {
                PhaseFlightPlayerState.endControl(player);
            }
        });
    }
}
