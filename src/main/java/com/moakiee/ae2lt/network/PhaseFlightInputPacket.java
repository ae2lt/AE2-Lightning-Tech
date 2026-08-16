package com.moakiee.ae2lt.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.celestweave.PhaseFlightControlRules;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;
import com.moakiee.ae2lt.celestweave.PhaseWingFlight;
import com.moakiee.ae2lt.celestweave.module.PhaseLockSubmodule;

/** Edge-triggered shared flight input. Hover intent is present only for a vanilla double-jump. */
public record PhaseFlightInputPacket(boolean jumpHeld, boolean hasFlightInput, boolean flying)
        implements CustomPacketPayload {
    public static final Type<PhaseFlightInputPacket> TYPE =
            new Type<>(NetworkInit.id("phase_flight_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhaseFlightInputPacket> STREAM_CODEC =
            StreamCodec.ofMember(PhaseFlightInputPacket::write, PhaseFlightInputPacket::decode);

    public static PhaseFlightInputPacket jump(boolean jumpHeld) {
        return new PhaseFlightInputPacket(jumpHeld, false, false);
    }

    public static PhaseFlightInputPacket flight(boolean jumpHeld, boolean flying) {
        return new PhaseFlightInputPacket(jumpHeld, true, flying);
    }

    private static PhaseFlightInputPacket decode(RegistryFriendlyByteBuf buf) {
        return new PhaseFlightInputPacket(buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(jumpHeld);
        buf.writeBoolean(hasFlightInput);
        buf.writeBoolean(flying);
    }

    @Override
    public Type<PhaseFlightInputPacket> type() {
        return TYPE;
    }

    public static void handle(PhaseFlightInputPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            boolean flightModuleActive = PhaseWingFlight.canUse(player);
            boolean flightLockActive = PhaseLockSubmodule.isFlightLockEnabled(player);
            PhaseFlightPlayerState.setJumpHeld(player, packet.jumpHeld() && flightModuleActive);
            if (!packet.hasFlightInput() || !flightModuleActive && !flightLockActive) {
                return;
            }
            PhaseFlightPlayerState.activate(player);
            boolean requestedFlying = packet.flying();
            if (PhaseFlightControlRules.rejectFlightToggle(
                    PhaseFlightMovementGuard.isPhaseModeEnabled(player),
                    PhaseFlightControlRules.intersectsWorldCollision(player),
                    requestedFlying)) {
                requestedFlying = true;
            }
            if (requestedFlying && player.isFallFlying()) {
                player.stopFallFlying();
            }
            PhaseFlightPlayerState.applyFlightInput(player, requestedFlying);
            player.onUpdateAbilities();
        });
    }
}
