package com.moakiee.ae2lt.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.celestweave.PhaseFlightControlRules;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;
import com.moakiee.ae2lt.celestweave.PhaseWingFlight;
import com.moakiee.ae2lt.celestweave.module.PhaseFlightSubmodule;
import com.moakiee.ae2lt.network.PhaseFlightInputPacket;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class ClientPhaseFlightHandler {
    private static boolean lastJumpHeld;

    private ClientPhaseFlightHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getEntity() != minecraft.player) {
            return;
        }

        var player = minecraft.player;
        boolean phaseModuleActive = CelestweaveArmorState.isAnyClientPhaseFlightActive();
        if (phaseModuleActive) {
            PhaseFlightPlayerState.activate(player);
        } else {
            PhaseFlightPlayerState.endControl(player);
        }
        syncJumpInput(minecraft, phaseModuleActive);
        PhaseWingFlight.tickThrust(player);
        if (isClientPhaseActive(player)) {
            PhaseFlightSubmodule.applyTransientPhaseState(player);
            return;
        }

        if (PhaseFlightSubmodule.hasTransientPhaseState(player)) {
            // Keep client collision disabled until the server finishes moving an in-wall player to
            // safety. The server remains authoritative and bounds this escape state.
            if (PhaseFlightControlRules.intersectsWorldCollision(player)) {
                PhaseFlightSubmodule.applyTransientPhaseState(player);
                return;
            }
            PhaseFlightSubmodule.clearTransientPhaseState(player);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        lastJumpHeld = false;
        CelestweaveArmorState.clearClientActiveCache();
        PhaseFlightMovementGuard.clear(event.getPlayer());
        PhaseFlightPlayerState.endControl(event.getPlayer());
        if (event.getPlayer() != null && PhaseFlightSubmodule.hasTransientPhaseState(event.getPlayer())) {
            PhaseFlightSubmodule.clearTransientPhaseState(event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        lastJumpHeld = false;
        CelestweaveArmorState.clearClientActiveCache();
        PhaseFlightMovementGuard.clear(event.getOldPlayer());
        PhaseFlightMovementGuard.clear(event.getNewPlayer());
        PhaseFlightPlayerState.endControl(event.getOldPlayer());
        PhaseFlightPlayerState.endControl(event.getNewPlayer());
        if (PhaseFlightSubmodule.hasTransientPhaseState(event.getOldPlayer())) {
            PhaseFlightSubmodule.clearTransientPhaseState(event.getOldPlayer());
        }
        if (PhaseFlightSubmodule.hasTransientPhaseState(event.getNewPlayer())) {
            PhaseFlightSubmodule.clearTransientPhaseState(event.getNewPlayer());
        }
    }

    private static boolean isClientPhaseActive(net.minecraft.world.entity.player.Player player) {
        return CelestweaveArmorState.isAnyClientPhaseFlightActive()
                && PhaseWingFlight.isFlightActive(player)
                && CelestweaveArmorState.getClientPhaseModeEnabled();
    }

    private static void syncJumpInput(Minecraft minecraft, boolean phaseModuleActive) {
        boolean jumpHeld = phaseModuleActive && minecraft.options.keyJump.isDown();
        PhaseFlightPlayerState.setJumpHeld(minecraft.player, jumpHeld);
        if (jumpHeld == lastJumpHeld) {
            return;
        }
        lastJumpHeld = jumpHeld;
        PacketDistributor.sendToServer(PhaseFlightInputPacket.jump(jumpHeld));
    }
}
