package com.moakiee.ae2lt.celestweave.module;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import com.moakiee.ae2lt.AE2LightningTech;

/**
 * Releases Forge's shared {@code mayfly} bit long enough for another provider to reclaim it.
 * Forge 1.20.1 has no source-aware flight attribute, so a full player tick is the closest
 * equivalent to querying NeoForge's current {@code mayFly()} result during a handoff.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class ForgeFlightPermissionHandoff {
    private static final Map<ServerPlayer, ProbeState> PENDING = new IdentityHashMap<>();

    private ForgeFlightPermissionHandoff() {
    }

    static void beginRelease(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (hasGameModeFlight(serverPlayer)) {
            PENDING.remove(serverPlayer);
            return;
        }
        serverPlayer.getAbilities().mayfly = true;
        PENDING.put(serverPlayer, ProbeState.WAITING_FOR_NEXT_TICK);
    }

    static void cancelRelease(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            PENDING.remove(serverPlayer);
        }
    }

    static boolean isReleasePending(Player player) {
        return player instanceof ServerPlayer serverPlayer && PENDING.containsKey(serverPlayer);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerTickStart(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ProbeState state = PENDING.get(player);
        if (state != ProbeState.WAITING_FOR_NEXT_TICK) {
            return;
        }
        if (hasGameModeFlight(player)) {
            finishProbe(player);
            return;
        }

        // Do not synchronize this temporary withdrawal. The client keeps its current flight
        // intent while every server-side provider gets one complete tick to reassert mayfly.
        player.getAbilities().mayfly = false;
        PENDING.put(player, ProbeState.PROBING);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTickEnd(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (PENDING.get(player) == ProbeState.PROBING) {
            finishProbe(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING.remove(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original) {
            PENDING.remove(original);
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING.remove(player);
        }
    }

    private static void finishProbe(ServerPlayer player) {
        var abilities = player.getAbilities();
        var target = FlightAbilityRestoreRules.targetAfterReleaseProbe(
                hasGameModeFlight(player),
                abilities.mayfly,
                abilities.flying);

        PENDING.remove(player);
        abilities.mayfly = target.mayfly();
        abilities.flying = target.flying();
        PhaseLockSubmodule.reconcileFlightLock(player);
        player.onUpdateAbilities();
    }

    private static boolean hasGameModeFlight(Player player) {
        return player.isCreative() || player.isSpectator();
    }

    private enum ProbeState {
        WAITING_FOR_NEXT_TICK,
        PROBING
    }
}
