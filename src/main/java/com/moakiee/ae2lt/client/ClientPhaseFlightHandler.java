package com.moakiee.ae2lt.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.celestweave.ArmorPhaseFlightRules;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.celestweave.PhaseFlightControlRules;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;
import com.moakiee.ae2lt.celestweave.module.PhaseFlightSubmodule;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class ClientPhaseFlightHandler {
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
            if (PhaseFlightPlayerState.isFlying(player)) {
                // Bosses such as Draconic Evolution's guardian directly clear vanilla's public
                // ability bit on both logical sides. Restore that projection from private intent.
                PhaseFlightPlayerState.syncVanillaAbilities(player);
            }
        } else {
            PhaseFlightPlayerState.endControl(player);
        }
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
        PhaseFlightMovementGuard.clear(event.getPlayer());
        PhaseFlightPlayerState.endControl(event.getPlayer());
        if (event.getPlayer() != null && PhaseFlightSubmodule.hasTransientPhaseState(event.getPlayer())) {
            PhaseFlightSubmodule.clearTransientPhaseState(event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone event) {
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
        return ArmorPhaseFlightRules.clientPhaseStateActive(
                CelestweaveArmorState.isAnyClientPhaseFlightActive(),
                PhaseFlightPlayerState.isFlying(player),
                CelestweaveArmorState.getClientPhaseModeEnabled());
    }
}
