package com.moakiee.ae2lt.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.celestweave.PhaseFlightControlRules;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;
import com.moakiee.ae2lt.celestweave.PhaseWingFlight;
import com.moakiee.ae2lt.celestweave.module.PhaseFlightSubmodule;
import com.moakiee.ae2lt.network.NetworkInit;
import com.moakiee.ae2lt.network.PhaseFlightInputPacket;

@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class ClientPhaseFlightHandler {
    private static boolean lastJumpHeld;
    private static long lastFlightControlGeneration =
            CelestweaveArmorState.getClientFlightControlGeneration();

    private ClientPhaseFlightHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.player != minecraft.player) {
            return;
        }

        var player = minecraft.player;
        boolean flightModuleActive = CelestweaveArmorState.isAnyClientFlightControlActive();
        if (flightModuleActive || PhaseFlightPlayerState.isFlightLocked(player)) {
            PhaseFlightPlayerState.activate(player);
        } else {
            PhaseFlightPlayerState.endControl(player);
        }
        syncJumpInput(minecraft, flightModuleActive);
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
        CelestweaveArmorState.clearClientActiveCache();
        resetJumpInputSync();
        PhaseFlightMovementGuard.clear(event.getPlayer());
        PhaseFlightPlayerState.endControl(event.getPlayer());
        if (event.getPlayer() != null && PhaseFlightSubmodule.hasTransientPhaseState(event.getPlayer())) {
            PhaseFlightSubmodule.clearTransientPhaseState(event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        CelestweaveArmorState.clearClientActiveCache();
        resetJumpInputSync();
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
        return CelestweaveArmorState.isAnyClientFlightControlActive()
                && PhaseWingFlight.isFlightActive(player)
                && CelestweaveArmorState.getClientPhaseModeEnabled();
    }

    private static void syncJumpInput(Minecraft minecraft, boolean flightModuleActive) {
        boolean jumpHeld = flightModuleActive && minecraft.options.keyJump.isDown();
        PhaseFlightPlayerState.setJumpHeld(minecraft.player, jumpHeld);
        long controlGeneration = CelestweaveArmorState.getClientFlightControlGeneration();
        if (!PhaseFlightControlRules.shouldSyncJumpInput(
                jumpHeld,
                lastJumpHeld,
                controlGeneration,
                lastFlightControlGeneration)) {
            return;
        }
        lastJumpHeld = jumpHeld;
        lastFlightControlGeneration = controlGeneration;
        NetworkInit.sendToServer(PhaseFlightInputPacket.jump(jumpHeld));
    }

    private static void resetJumpInputSync() {
        lastJumpHeld = false;
        lastFlightControlGeneration = CelestweaveArmorState.getClientFlightControlGeneration();
    }
}
