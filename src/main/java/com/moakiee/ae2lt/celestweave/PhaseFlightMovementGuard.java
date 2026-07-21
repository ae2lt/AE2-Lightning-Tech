package com.moakiee.ae2lt.celestweave;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
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
    private static final ThreadLocal<IdentityHashMap<Player, Integer>> PLAYER_PAYLOAD_TELEPORT_DEPTH =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static final ThreadLocal<Player> MOVEMENT_PACKET_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<Player> CUSTOM_PAYLOAD_PLAYER = new ThreadLocal<>();
    private static final StackWalker MOVEMENT_PACKET_STACK_WALKER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final int MOVEMENT_PACKET_STACK_SCAN_LIMIT = 24;

    private PhaseFlightMovementGuard() {
    }

    public static void updatePhaseFlightState(
            Player player,
            boolean phaseModeEnabled,
            boolean phaseFlying) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        SERVER_SETTINGS.compute(player.getUUID(), (id, current) ->
                currentFor(player, current).withPhaseFlight(phaseModeEnabled, phaseFlying));
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
        PLAYER_PAYLOAD_TELEPORT_DEPTH.get().remove(player);
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
        return settings != null && settings.owner() == player && settings.blockTeleports();
    }

    public static boolean isPhaseFlightActive(Player player) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            return isPhaseModeEnabled(player) && PhaseFlightPlayerState.isFlying(player);
        }
        ServerSettings settings = SERVER_SETTINGS.get(player.getUUID());
        return settings != null
                && settings.owner() == player
                && settings.phaseModeEnabled()
                && settings.phaseFlying();
    }

    /** Phase-mode availability without requiring the current flying bit to remain set. */
    public static boolean isPhaseModeEnabled(Player player) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            return CelestweaveArmorState.isAnyClientPhaseFlightActive()
                    && CelestweaveArmorState.getClientPhaseModeEnabled();
        }
        ServerSettings settings = SERVER_SETTINGS.get(player.getUUID());
        return settings != null && settings.owner() == player && settings.phaseModeEnabled();
    }

    public static boolean isSelfMovementAuthorized(Player player) {
        if (player == null) {
            return false;
        }
        if (SELF_MOVEMENT_DEPTH.get().getOrDefault(player, 0) > 0) {
            return true;
        }
        return isCurrentMovementPacket(player);
    }

    /**
     * Teleport authorization is intentionally broader than force authorization. A serverbound
     * custom payload may represent an explicit player action such as activating a waystone, but it
     * must only authorize teleporting the exact player who sent that payload.
     */
    public static boolean isSelfTeleportAuthorized(Player player) {
        if (player == null) {
            return false;
        }
        if (SELF_MOVEMENT_DEPTH.get().getOrDefault(player, 0) > 0
                || PLAYER_PAYLOAD_TELEPORT_DEPTH.get().getOrDefault(player, 0) > 0) {
            return true;
        }
        return isCurrentMovementPacket(player) || isCurrentCustomPayload(player);
    }

    /**
     * Marks the exact player whose serverbound movement packet is currently being handled.
     *
     * <p>The identity check prevents one player's packet from authorizing movement of another
     * player. The marker alone is deliberately insufficient: {@link #isCurrentMovementPacket}
     * also verifies that the current stack is still inside vanilla's movement-packet handler, so
     * an exceptional return cannot leave a stale ThreadLocal authorization behind.</p>
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
        // This is the hot-path guard: do not walk the stack unless a movement-packet scope exists
        // and it belongs to this exact ServerPlayer instance.
        if (!(player instanceof ServerPlayer) || MOVEMENT_PACKET_PLAYER.get() != player) {
            return false;
        }
        return MOVEMENT_PACKET_STACK_WALKER.walk(frames -> frames
                .limit(MOVEMENT_PACKET_STACK_SCAN_LIMIT)
                .anyMatch(frame -> frame.getDeclaringClass() == ServerGamePacketListenerImpl.class
                        && frame.getMethodName().equals("handleMovePlayer")));
    }

    private static boolean isCurrentCustomPayload(Player player) {
        if (!(player instanceof ServerPlayer) || CUSTOM_PAYLOAD_PLAYER.get() != player) {
            return false;
        }
        return MOVEMENT_PACKET_STACK_WALKER.walk(frames -> frames
                .limit(MOVEMENT_PACKET_STACK_SCAN_LIMIT)
                .anyMatch(frame -> frame.getDeclaringClass() == ServerGamePacketListenerImpl.class
                        && frame.getMethodName().equals("handleCustomPayload")));
    }

    /**
     * Distinguishes the coordinate write at the end of ordinary entity movement from a direct
     * coordinate teleport. Unauthorized movement itself is handled at {@code Entity.move}; the
     * nested {@code setPosRaw} must not also be reported as a blocked teleport.
     */
    public static boolean isMovementPositionUpdate() {
        return MOVEMENT_PACKET_STACK_WALKER.walk(frames -> frames
                .limit(MOVEMENT_PACKET_STACK_SCAN_LIMIT)
                .anyMatch(frame -> frame.getDeclaringClass() == Entity.class
                        && frame.getMethodName().equals("move")));
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

    public static void runAsPlayerPayloadTeleport(Player player, Runnable action) {
        beginPlayerPayloadTeleport(player);
        try {
            action.run();
        } finally {
            endPlayerPayloadTeleport(player);
        }
    }

    public static <T> T runAsPlayerPayloadTeleport(Player player, Supplier<T> action) {
        beginPlayerPayloadTeleport(player);
        try {
            return action.get();
        } finally {
            endPlayerPayloadTeleport(player);
        }
    }

    private static void beginPlayerPayloadTeleport(Player player) {
        if (player != null && !player.level().isClientSide()) {
            PLAYER_PAYLOAD_TELEPORT_DEPTH.get().merge(player, 1, Integer::sum);
        }
    }

    private static void endPlayerPayloadTeleport(Player player) {
        if (player == null) {
            return;
        }
        var depths = PLAYER_PAYLOAD_TELEPORT_DEPTH.get();
        int next = depths.getOrDefault(player, 0) - 1;
        if (next <= 0) {
            depths.remove(player);
            if (depths.isEmpty()) {
                PLAYER_PAYLOAD_TELEPORT_DEPTH.remove();
            }
        } else {
            depths.put(player, next);
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
            boolean phaseFlying,
            boolean blockForces,
            boolean blockTeleports) {
        private static ServerSettings empty(Player owner) {
            return new ServerSettings(owner, false, false, false, false);
        }

        private ServerSettings withPhaseFlight(boolean phaseModeEnabled, boolean phaseFlying) {
            return new ServerSettings(owner, phaseModeEnabled, phaseFlying, blockForces, blockTeleports);
        }

        private ServerSettings withPhaseLockProtection(boolean blockForces, boolean blockTeleports) {
            return new ServerSettings(owner, phaseModeEnabled, phaseFlying, blockForces, blockTeleports);
        }

        private boolean isEmpty() {
            return !phaseModeEnabled && !phaseFlying && !blockForces && !blockTeleports;
        }
    }

    private record BlockedTeleportNotice(String dimension, String position, boolean includeDimension) {
    }
}
