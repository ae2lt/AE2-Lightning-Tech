package com.moakiee.ae2lt.celestweave;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Separates player-authorized phase-flight movement from force and coordinate changes initiated by
 * the world or other systems.
 */
public final class PhaseFlightMovementGuard {
    private static final Map<UUID, ServerSettings> SERVER_SETTINGS = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockedTeleportNotice> LAST_BLOCKED_TELEPORT_NOTICE =
            new ConcurrentHashMap<>();
    private static final ThreadLocal<IdentityHashMap<Player, Integer>> SELF_MOVEMENT_DEPTH =
            ThreadLocal.withInitial(IdentityHashMap::new);

    private PhaseFlightMovementGuard() {
    }

    public static void updateServerSettings(Player player, boolean blockForces, boolean blockTeleports) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        SERVER_SETTINGS.put(player.getUUID(), new ServerSettings(player, blockForces, blockTeleports));
        if (!blockTeleports) {
            LAST_BLOCKED_TELEPORT_NOTICE.remove(player.getUUID());
        }
    }

    public static void clear(Player player) {
        if (player == null) {
            return;
        }
        SERVER_SETTINGS.remove(player.getUUID());
        LAST_BLOCKED_TELEPORT_NOTICE.remove(player.getUUID());
        SELF_MOVEMENT_DEPTH.get().remove(player);
    }

    public static boolean blocksExternalForces(Player player) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            return CelestweaveArmorState.isAnyClientPhaseFlightActive()
                    && CelestweaveArmorState.getClientPhaseBlockExternalForces();
        }
        ServerSettings settings = SERVER_SETTINGS.get(player.getUUID());
        return settings != null && settings.owner() == player && settings.blockForces();
    }

    public static boolean blocksExternalTeleports(Player player) {
        if (player == null || player.level().isClientSide()) {
            return false;
        }
        ServerSettings settings = SERVER_SETTINGS.get(player.getUUID());
        return settings != null && settings.owner() == player && settings.blockTeleports();
    }

    public static boolean isPhaseFlightActive(Player player) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            return CelestweaveArmorState.isAnyClientPhaseFlightActive();
        }
        ServerSettings settings = SERVER_SETTINGS.get(player.getUUID());
        return settings != null && settings.owner() == player;
    }

    public static boolean isSelfMovementAuthorized(Player player) {
        return SELF_MOVEMENT_DEPTH.get().getOrDefault(player, 0) > 0;
    }

    public static void beginSelfMovement(Player player) {
        if (player == null) {
            return;
        }
        SELF_MOVEMENT_DEPTH.get().merge(player, 1, Integer::sum);
    }

    public static void endSelfMovement(Player player) {
        if (player == null) {
            return;
        }
        var depths = SELF_MOVEMENT_DEPTH.get();
        int next = depths.getOrDefault(player, 0) - 1;
        if (next <= 0) {
            depths.remove(player);
            if (depths.isEmpty()) {
                SELF_MOVEMENT_DEPTH.remove();
            }
        } else {
            depths.put(player, next);
        }
    }

    public static void runAsSelfMovement(Player player, Runnable movement) {
        beginSelfMovement(player);
        try {
            movement.run();
        } finally {
            endSelfMovement(player);
        }
    }

    public static void notifyBlockedTeleport(ServerPlayer player, Vec3 target) {
        notifyBlockedTeleport(player, player.serverLevel(), target, false);
    }

    public static void notifyBlockedDimensionTeleport(
            ServerPlayer player,
            ServerLevel targetLevel,
            Vec3 target) {
        notifyBlockedTeleport(player, targetLevel, target, true);
    }

    private static void notifyBlockedTeleport(
            ServerPlayer player,
            ServerLevel targetLevel,
            Vec3 target,
            boolean includeDimension) {
        // A replacement ServerPlayer is constructed before PlayerList wires its connection during
        // respawn. Never try to report through that half-constructed instance.
        if (player.connection == null) {
            return;
        }
        String dimension = targetLevel.dimension().location().toString();
        String position = formatPosition(target);
        var notice = new BlockedTeleportNotice(dimension, position, includeDimension);
        BlockedTeleportNotice previous = LAST_BLOCKED_TELEPORT_NOTICE.put(player.getUUID(), notice);
        if (notice.equals(previous)) {
            return;
        }

        Component message = includeDimension
                ? Component.translatable(
                        "ae2lt.celestweave.phase_flight.teleport_blocked.dimension",
                        dimension,
                        position)
                : Component.translatable(
                        "ae2lt.celestweave.phase_flight.teleport_blocked",
                        position);
        player.displayClientMessage(message, true);
    }

    private static String formatPosition(Vec3 target) {
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", target.x, target.y, target.z);
    }

    private record ServerSettings(Player owner, boolean blockForces, boolean blockTeleports) {
    }

    private record BlockedTeleportNotice(String dimension, String position, boolean includeDimension) {
    }
}
