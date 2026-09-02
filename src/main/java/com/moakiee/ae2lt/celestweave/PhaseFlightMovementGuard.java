package com.moakiee.ae2lt.celestweave;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;

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
    private static final ThreadLocal<ServerPlayer> MAIN_THREAD_PAYLOAD_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<IdentityHashMap<Player, Integer>> MOVEMENT_POSITION_UPDATE_DEPTH =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static final ThreadLocal<IdentityHashMap<Player, Integer>> ENVIRONMENT_MOVEMENT_DEPTH =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static final ThreadLocal<IdentityHashMap<Player, Integer>> VANILLA_TRAVEL_MOVEMENT_DEPTH =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static final ThreadLocal<Player> MOVEMENT_PACKET_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<Player> CUSTOM_PAYLOAD_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<CommandSourceStack> COMMAND_SOURCE = new ThreadLocal<>();

    private PhaseFlightMovementGuard() {
    }

    public static void updatePhaseFlightState(
            Player player,
            boolean phaseModeEnabled,
            boolean phaseTraversalActive) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        SERVER_SETTINGS.compute(player.getUUID(), (id, current) ->
                currentFor(player, current).withPhaseFlight(phaseModeEnabled, phaseTraversalActive));
    }

    public static void clearPhaseFlightState(Player player) {
        clearContribution(player, current -> current.withPhaseFlight(false, false));
    }

    public static void updatePhaseLockProtection(
            Player player,
            boolean blockForces,
            boolean blockTeleports) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        SERVER_SETTINGS.compute(player.getUUID(), (id, current) ->
                currentFor(player, current).withPhaseLockProtection(blockForces, blockTeleports));
        if (!blockTeleports) {
            LAST_BLOCKED_TELEPORT_NOTICE.remove(player.getUUID());
        }
    }

    public static void clearPhaseLockProtection(Player player) {
        clearContribution(player, current -> current.withPhaseLockProtection(false, false));
        if (player != null) {
            LAST_BLOCKED_TELEPORT_NOTICE.remove(player.getUUID());
        }
    }

    private static void clearContribution(
            Player player,
            UnaryOperator<ServerSettings> clearOperation) {
        if (player == null) {
            return;
        }
        SERVER_SETTINGS.computeIfPresent(player.getUUID(), (id, current) -> {
            if (current.owner() != player) {
                return null;
            }
            ServerSettings updated = clearOperation.apply(current);
            return updated.isEmpty() ? null : updated;
        });
    }

    private static ServerSettings currentFor(Player player, ServerSettings current) {
        return current != null && current.owner() == player ? current : ServerSettings.empty(player);
    }

    public static void clear(Player player) {
        if (player == null) {
            return;
        }
        SERVER_SETTINGS.remove(player.getUUID());
        LAST_BLOCKED_TELEPORT_NOTICE.remove(player.getUUID());
        SELF_MOVEMENT_DEPTH.get().remove(player);
        if (MAIN_THREAD_PAYLOAD_PLAYER.get() == player) {
            MAIN_THREAD_PAYLOAD_PLAYER.remove();
        }
        MOVEMENT_POSITION_UPDATE_DEPTH.get().remove(player);
        ENVIRONMENT_MOVEMENT_DEPTH.get().remove(player);
        VANILLA_TRAVEL_MOVEMENT_DEPTH.get().remove(player);
        if (MOVEMENT_PACKET_PLAYER.get() == player) {
            MOVEMENT_PACKET_PLAYER.remove();
        }
        if (CUSTOM_PAYLOAD_PLAYER.get() == player) {
            CUSTOM_PAYLOAD_PLAYER.remove();
        }
    }

    public static boolean blocksExternalForces(Player player) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            return CelestweaveArmorState.getClientPhaseLockBlockExternalForces();
        }
        ServerSettings settings = SERVER_SETTINGS.get(player.getUUID());
        return settings != null && settings.owner() == player && settings.blockForces();
    }

    public static boolean blocksExternalTeleports(Player player) {
        if (player == null || player.level().isClientSide()) {
            return false;
        }
        ServerSettings settings = SERVER_SETTINGS.get(player.getUUID());
        if (settings == null || settings.owner() != player || !settings.blockTeleports()) {
            return false;
        }
        var mode = AE2LTCommonConfig.overloadArmorPhaseLockTeleportMode();
        if (mode.disablesProtection()) {
            return false;
        }
        return !mode.ignoresPrivilegedCommands() || !isPrivilegedCommandExecution();
    }

    public static boolean isPhaseFlightActive(Player player) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            return isPhaseModeEnabled(player) && PhaseWingFlight.isFlightActive(player);
        }
        ServerSettings settings = SERVER_SETTINGS.get(player.getUUID());
        return settings != null
                && settings.owner() == player
                && settings.phaseModeEnabled()
                && settings.phaseTraversalActive();
    }

    /** Phase-mode availability without requiring the current flying bit to remain set. */
    public static boolean isPhaseModeEnabled(Player player) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            return CelestweaveArmorState.isAnyClientFlightControlActive()
                    && CelestweaveArmorState.getClientPhaseModeEnabled();
        }
        ServerSettings settings = SERVER_SETTINGS.get(player.getUUID());
        return settings != null && settings.owner() == player && settings.phaseModeEnabled();
    }

    public static boolean isSelfMovementAuthorized(Player player) {
        if (player == null) {
            return false;
        }
        if (SELF_MOVEMENT_DEPTH.get().getOrDefault(player, 0) > 0
                || ENVIRONMENT_MOVEMENT_DEPTH.get().getOrDefault(player, 0) > 0) {
            return true;
        }
        if (consumeVanillaTravelMovement(player)) {
            return true;
        }
        return isCurrentMovementPacket(player);
    }


    /**
     * Teleport authorization is intentionally broader than force authorization. A serverbound
     * custom payload or command may represent an explicit player action, but it must only authorize
     * teleporting the exact player who initiated that action.
     */
    public static boolean isSelfTeleportAuthorized(Player player) {
        if (player == null) {
            return false;
        }
        if (SELF_MOVEMENT_DEPTH.get().getOrDefault(player, 0) > 0
                || MAIN_THREAD_PAYLOAD_PLAYER.get() == player) {
            return true;
        }
        CommandSourceStack commandSource = COMMAND_SOURCE.get();
        if (commandSource != null && commandSource.getEntity() == player) {
            return true;
        }
        return isCurrentMovementPacket(player) || isCurrentCustomPayload(player);
    }

    private static boolean isPrivilegedCommandExecution() {
        CommandSourceStack commandSource = COMMAND_SOURCE.get();
        return commandSource != null && commandSource.hasPermission(Commands.LEVEL_GAMEMASTERS);
    }

    /**
     * Marks the exact player whose serverbound movement packet is currently being handled.
     *
     * <p>The identity check prevents one player's packet from authorizing movement of another
     * player. The packet mixin owns this scope with a {@code try/finally} wrapper, avoiding both
     * stale authorization and runtime method-name checks that do not survive Forge 1.20.1's
     * production remapping.</p>
     */
    public static void beginMovementPacket(Player player) {
        if (player != null && !player.level().isClientSide()) {
            MOVEMENT_PACKET_PLAYER.set(player);
        }
    }

    public static void endMovementPacket(Player player) {
        if (MOVEMENT_PACKET_PLAYER.get() == player) {
            MOVEMENT_PACKET_PLAYER.remove();
        }
    }

    public static void beginCustomPayload(Player player) {
        if (player != null && !player.level().isClientSide()) {
            CUSTOM_PAYLOAD_PLAYER.set(player);
        }
    }

    public static void endCustomPayload(Player player) {
        if (CUSTOM_PAYLOAD_PLAYER.get() == player) {
            CUSTOM_PAYLOAD_PLAYER.remove();
        }
    }

    private static boolean isCurrentMovementPacket(Player player) {
        return player instanceof ServerPlayer && MOVEMENT_PACKET_PLAYER.get() == player;
    }

    private static boolean isCurrentCustomPayload(Player player) {
        return player instanceof ServerPlayer && CUSTOM_PAYLOAD_PLAYER.get() == player;
    }

    /**
     * Distinguishes the coordinate write at the end of ordinary entity movement from a direct
     * coordinate teleport. Unauthorized movement itself is handled at {@code Entity.move}; the
     * nested {@code setPosRaw} must not also be reported as a blocked teleport.
     */
    public static boolean isMovementPositionUpdate(Player player) {
        return player != null
                && MOVEMENT_POSITION_UPDATE_DEPTH.get().getOrDefault(player, 0) > 0;
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

    /** Runs only the original fluid and bubble-column velocity updates for this player. */
    public static void runAsEnvironmentMovement(Player player, Runnable movement) {
        if (player == null) {
            movement.run();
            return;
        }
        var depths = ENVIRONMENT_MOVEMENT_DEPTH.get();
        depths.merge(player, 1, Integer::sum);
        try {
            movement.run();
        } finally {
            int next = depths.getOrDefault(player, 0) - 1;
            if (next <= 0) {
                depths.remove(player);
                if (depths.isEmpty()) {
                    ENVIRONMENT_MOVEMENT_DEPTH.remove();
                }
            } else {
                depths.put(player, next);
            }
        }
    }


    /** Authorizes one direct vanilla travel mutation without authorizing nested third-party code. */
    public static void runAsVanillaTravelMovement(Player player, Runnable movement) {
        if (player == null) {
            movement.run();
            return;
        }
        var permits = VANILLA_TRAVEL_MOVEMENT_DEPTH.get();
        int previous = permits.getOrDefault(player, 0);
        permits.put(player, previous + 1);
        try {
            movement.run();
        } finally {
            if (previous == 0) {
                permits.remove(player);
                if (permits.isEmpty()) {
                    VANILLA_TRAVEL_MOVEMENT_DEPTH.remove();
                }
            } else {
                permits.put(player, previous);
            }
        }
    }

    private static boolean consumeVanillaTravelMovement(Player player) {
        var permits = VANILLA_TRAVEL_MOVEMENT_DEPTH.get();
        int remaining = permits.getOrDefault(player, 0);
        if (remaining <= 0) {
            return false;
        }
        if (remaining == 1) {
            permits.remove(player);
            if (permits.isEmpty()) {
                VANILLA_TRAVEL_MOVEMENT_DEPTH.remove();
            }
        } else {
            permits.put(player, remaining - 1);
        }
        return true;
    }

    /** Marks only the nested position writes performed by {@code Entity.move}. */
    public static void runAsMovementPositionUpdate(Player player, Runnable movement) {
        if (player == null) {
            movement.run();
            return;
        }
        var depths = MOVEMENT_POSITION_UPDATE_DEPTH.get();
        depths.merge(player, 1, Integer::sum);
        try {
            movement.run();
        } finally {
            int next = depths.getOrDefault(player, 0) - 1;
            if (next <= 0) {
                depths.remove(player);
                if (depths.isEmpty()) {
                    MOVEMENT_POSITION_UPDATE_DEPTH.remove();
                }
            } else {
                depths.put(player, next);
            }
        }
    }

    /** Runs a main-thread payload handler with its exact sending player bound to this call scope. */
    public static void runAsPlayerPayloadHandler(ServerPlayer player, Runnable action) {
        ServerPlayer previous = MAIN_THREAD_PAYLOAD_PLAYER.get();
        MAIN_THREAD_PAYLOAD_PLAYER.set(player);
        try {
            action.run();
        } finally {
            if (previous == null) {
                MAIN_THREAD_PAYLOAD_PLAYER.remove();
            } else {
                MAIN_THREAD_PAYLOAD_PLAYER.set(previous);
            }
        }
    }

    /** Runs one executable command with its effective source bound to teleport authorization. */
    public static void runAsCommandExecution(CommandSourceStack source, Runnable action) {
        runAsCommandExecution(source, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Forge 1.20.1 variant: {@code Commands.performPrefixedCommand} returns the result
     * code, so the wrapper must be able to produce a value (1.21's
     * {@code executeCommandInContext} returns void).
     */
    public static <T> T runAsCommandExecution(CommandSourceStack source, Supplier<T> action) {
        CommandSourceStack previous = COMMAND_SOURCE.get();
        COMMAND_SOURCE.set(source);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                COMMAND_SOURCE.remove();
            } else {
                COMMAND_SOURCE.set(previous);
            }
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

    private record ServerSettings(
            Player owner,
            boolean phaseModeEnabled,
            boolean phaseTraversalActive,
            boolean blockForces,
            boolean blockTeleports) {
        private static ServerSettings empty(Player owner) {
            return new ServerSettings(owner, false, false, false, false);
        }

        private ServerSettings withPhaseFlight(boolean phaseModeEnabled, boolean phaseTraversalActive) {
            return new ServerSettings(owner, phaseModeEnabled, phaseTraversalActive, blockForces, blockTeleports);
        }

        private ServerSettings withPhaseLockProtection(boolean blockForces, boolean blockTeleports) {
            return new ServerSettings(owner, phaseModeEnabled, phaseTraversalActive, blockForces, blockTeleports);
        }

        private boolean isEmpty() {
            return !phaseModeEnabled && !phaseTraversalActive && !blockForces && !blockTeleports;
        }
    }

    private record BlockedTeleportNotice(String dimension, String position, boolean includeDimension) {
    }
}
