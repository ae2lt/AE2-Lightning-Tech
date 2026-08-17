package com.moakiee.ae2lt.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import com.moakiee.ae2lt.celestweave.PhaseFlightControlRules;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;
import com.moakiee.ae2lt.celestweave.PhaseWingFlight;
import com.moakiee.ae2lt.celestweave.module.PhaseLockSubmodule;

/** Edge-triggered shared flight input. Hover intent is present only for a vanilla double-jump. */
public record PhaseFlightInputPacket(boolean jumpHeld, boolean hasFlightInput, boolean flying)
{

    public static PhaseFlightInputPacket jump(boolean jumpHeld) {
        return new PhaseFlightInputPacket(jumpHeld, false, false);
    }

    public static PhaseFlightInputPacket flight(boolean jumpHeld, boolean flying) {
        return new PhaseFlightInputPacket(jumpHeld, true, flying);
    }

    public static PhaseFlightInputPacket decode(FriendlyByteBuf buf) {
        return new PhaseFlightInputPacket(buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(jumpHeld);
        buf.writeBoolean(hasFlightInput);
        buf.writeBoolean(flying);
    }

    public static void handle(PhaseFlightInputPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
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
        ctx.setPacketHandled(true);
    }
}
