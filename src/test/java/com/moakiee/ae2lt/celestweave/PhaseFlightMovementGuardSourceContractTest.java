package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PhaseFlightMovementGuardSourceContractTest {
    @Test
    void movementProtectionUsesDedicatedRuntimeStateRatherThanInstalledOrEscapeState() throws Exception {
        String guard = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/PhaseFlightMovementGuard.java"));
        assertTrue(guard.contains("Map<UUID, ServerSettings> SERVER_SETTINGS"));
        assertTrue(guard.contains("updatePhaseFlightState("));
        assertTrue(guard.contains("updatePhaseLockProtection("));
        assertTrue(guard.contains("withPhaseFlight(phaseModeEnabled, phaseTraversalActive)"));
        assertTrue(guard.contains("withPhaseLockProtection(blockForces, blockTeleports)"));
        assertTrue(guard.contains("clearPhaseFlightState(Player player)"));
        assertTrue(guard.contains("clearPhaseLockProtection(Player player)"));
        assertTrue(guard.contains("SERVER_SETTINGS.remove(player.getUUID())"));
        assertTrue(guard.contains("settings.owner() == player"));
        assertTrue(guard.contains("if (player.connection == null)"));
        assertTrue(guard.contains("public static boolean isPhaseFlightActive(Player player)"));
        assertTrue(guard.contains("Map<UUID, BlockedTeleportNotice> LAST_BLOCKED_TELEPORT_NOTICE"));
        assertTrue(guard.contains("if (notice.equals(previous))"));
        assertTrue(guard.contains("LAST_BLOCKED_TELEPORT_NOTICE.remove(player.getUUID())"));
        assertTrue(guard.contains("teleport_blocked.dimension"));
        assertTrue(guard.contains("CelestweaveArmorState.isAnyClientFlightControlActive()"));
        assertTrue(guard.contains("CelestweaveArmorState.getClientPhaseLockBlockExternalForces()"));
        assertFalse(guard.contains("StackWalker.Option.RETAIN_CLASS_REFERENCE"));
        assertTrue(guard.contains("MOVEMENT_PACKET_PLAYER.get() == player"));
        assertTrue(guard.contains("CUSTOM_PAYLOAD_PLAYER.get() == player"));
        assertTrue(guard.contains("MOVEMENT_POSITION_UPDATE_DEPTH"));
        assertTrue(guard.contains("ThreadLocal<ServerPlayer> MAIN_THREAD_PAYLOAD_PLAYER"));
        assertTrue(guard.contains("CommandSourceStack commandSource = COMMAND_SOURCE.get()"));
        assertTrue(guard.contains("commandSource.getEntity() == player"));
        assertTrue(guard.contains("commandSource.hasPermission(Commands.LEVEL_GAMEMASTERS)"));
        assertTrue(guard.contains("AE2LTCommonConfig.overloadArmorPhaseLockTeleportMode()"));
        assertFalse(guard.contains("PhaseFlightSubmodule.hasTransientPhaseState(player)"));
        assertFalse(guard.contains("isSubmoduleInstalled"));
        assertFalse(guard.contains("getPersistentData()"));

        String phaseFlight = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/module/PhaseFlightSubmodule.java"));
        int revoke = phaseFlight.indexOf("private static void revokePhaseFlight");
        int clearGuard = phaseFlight.indexOf("PhaseFlightMovementGuard.clearPhaseFlightState(player)", revoke);
        int escape = phaseFlight.indexOf("beginEscapePhase(player)", revoke);
        assertTrue(revoke >= 0 && clearGuard > revoke && escape > clearGuard);
        assertFalse(phaseFlight.contains("BLOCK_EXTERNAL_FORCES_CONFIG_KEY"));
        assertFalse(phaseFlight.contains("BLOCK_EXTERNAL_TELEPORTS_CONFIG_KEY"));

        int beginEscape = phaseFlight.indexOf("private static void beginEscapePhase");
        int endEscape = phaseFlight.indexOf("private static void updateMovementGuards", beginEscape);
        String escapeBody = phaseFlight.substring(beginEscape, endEscape);
        assertFalse(escapeBody.contains("updateMovementGuards"));
    }

    @Test
    void vanillaFlightBitsAreOnlyAProjectionOfPrivatePlayerIntent() throws Exception {
        String state = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/PhaseFlightPlayerState.java"));
        String playerMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/PlayerPhaseFlightMixin.java"));
        String phaseFlight = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/module/PhaseFlightSubmodule.java"));
        String clientHandler = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/ClientPhaseFlightHandler.java"));
        String packetMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/ServerGamePacketListenerPhaseMovementMixin.java"));
        String clientMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/client/LocalPlayerPhaseMovementMixin.java"));
        String inputPacket = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/network/PhaseFlightInputPacket.java"));
        String settingsPacket = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/network/FlightInertiaSyncPacket.java"));
        String clientPacketHandlers = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/ClientNetworkPacketHandlers.java"));
        String mixinConfig = Files.readString(Path.of("src/main/resources/ae2lt.mixins.json"));

        assertTrue(state.contains("interface Access"));
        assertTrue(state.contains("public static void activate(Player player)"));
        assertTrue(state.contains("access.ae2lt$setPhaseFlying(access.ae2lt$getVanillaFlying())"));
        assertFalse(state.contains("mayfly"));
        assertTrue(state.contains("access.ae2lt$setVanillaFlying(flying)"));
        assertTrue(state.contains("access.ae2lt$setVanillaFlying(access.ae2lt$isPhaseFlying())"));
        assertTrue(state.contains("access.ae2lt$isPhaseFlightLocked()"));
        assertTrue(state.contains("public static boolean readEffectiveFlying"));
        assertTrue(state.contains("public static void applyFlightInput"));
        assertTrue(state.contains("public static void synchronizeFlying"));
        assertTrue(state.contains("public static void endControl(Player player, boolean flying)"));
        assertTrue(state.contains("access.ae2lt$setVanillaFlying(flying)"));
        assertTrue(playerMixin.contains("implements PhaseFlightPlayerState.Access"));
        assertTrue(playerMixin.contains("private boolean ae2lt$phaseFlying"));
        assertFalse(playerMixin.contains("maintainVanillaAbilities"));
        assertTrue(playerMixin.contains("method = \"getAbilities\""));
        assertTrue(playerMixin.contains("abilities.flying = ae2lt$phaseFlying"));
        assertTrue(playerMixin.contains("return abilities.flying"));
        assertTrue(playerMixin.contains("@ModifyExpressionValue"));
        assertTrue(playerMixin.contains("opcode = Opcodes.GETFIELD"));
        assertTrue(playerMixin.contains("PhaseFlightPlayerState.readEffectiveFlying"));
        assertTrue(phaseFlight.contains("PhaseFlightPlayerState.isFlying(player)"));
        assertTrue(clientHandler.contains("PhaseWingFlight.isFlightActive(player)"));
        assertTrue(clientMixin.contains("PhaseFlightPlayerState.applyFlightInput"));
        assertTrue(clientMixin.contains("PhaseFlightInputPacket.flight"));
        assertTrue(inputPacket.contains("PhaseFlightPlayerState.applyFlightInput"));
        assertFalse(packetMixin.contains("applyFlightInput"));
        assertTrue(packetMixin.contains("PhaseFlightPlayerState.isFlightLocked(player)"));
        assertFalse(packetMixin.contains("reconcileVanillaAbilities"));
        assertTrue(settingsPacket.contains("boolean flightControlActive"));
        assertTrue(settingsPacket.contains("boolean flying"));
        assertTrue(settingsPacket.contains("boolean flightLockEnabled"));
        assertTrue(settingsPacket.contains("ClientNetworkPacketHandlers.handleFlightInertia(payload)"));
        assertTrue(clientPacketHandlers.contains("PhaseFlightPlayerState.synchronizeFlying(player, packet.flying())"));
        int settingsHandler = settingsPacket.indexOf("public static void handle");
        int inactiveBranch = clientPacketHandlers.indexOf("} else {");
        int inactiveEndControl = clientPacketHandlers.indexOf(
                "PhaseFlightPlayerState.endControl(player, packet.flying())",
                inactiveBranch);
        assertTrue(settingsHandler >= 0);
        assertTrue(inactiveBranch >= 0);
        assertTrue(inactiveEndControl > inactiveBranch);
        assertFalse(mixinConfig.contains("DraconicChargeUpPhaseFlightMixin"));
        assertFalse(phaseFlight.contains("&& player.getAbilities().flying\n                && isPhaseModeConfigured"));
        assertTrue(phaseFlight.contains("abilities.mayfly = mayfly"));
        assertFalse(phaseFlight.contains("IPlayerExtension"));
        assertFalse(mixinConfig.contains("PlayerMayFlyMixin"));
    }

    @Test
    void everyServerTeleportGuardReportsItsActualTarget() throws Exception {
        String entityMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/EntityPhaseMovementMixin.java"));
        String packetMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/ServerGamePacketListenerPhaseMovementMixin.java"));
        String dimensionMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/ServerPlayerPhaseMovementMixin.java"));

        assertTrue(entityMixin.contains("notifyBlockedTeleport(serverPlayer, new Vec3(x, y, z))"));
        assertTrue(entityMixin.contains("@WrapMethod(method = \"move(Lnet/minecraft/world/entity/MoverType;"));
        assertTrue(entityMixin.contains("PhaseFlightMovementGuard.blocksExternalForces(player)"));
        assertTrue(entityMixin.contains("PhaseFlightMovementGuard.isMovementPositionUpdate(player)"));
        assertTrue(entityMixin.contains("PhaseFlightMovementGuard.runAsMovementPositionUpdate("));
        assertTrue(entityMixin.contains("PhaseFlightMovementGuard.isSelfTeleportAuthorized(player)"));
        assertTrue(packetMixin.contains("relativeMovements.contains(RelativeMovement.X)"));
        assertTrue(packetMixin.contains("notifyBlockedTeleport(player, target)"));
        assertTrue(packetMixin.contains("!player.position().equals(target)"));
        assertTrue(dimensionMixin.contains("notifyBlockedDimensionTeleport("));
        assertTrue(dimensionMixin.contains("destination"));
        assertTrue(dimensionMixin.contains("player.position()"));
    }

    @Test
    void movementPacketsUseAPlayerBoundFinallySafeAuthorization() throws Exception {
        String guard = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/PhaseFlightMovementGuard.java"));
        String packetMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/ServerGamePacketListenerPhaseMovementMixin.java"));

        assertTrue(guard.contains("MOVEMENT_PACKET_PLAYER.get() == player"));
        assertFalse(guard.contains("MOVEMENT_PACKET_STACK_WALKER"));
        assertTrue(packetMixin.contains("@WrapMethod(method = \"handleMovePlayer\")"));
        assertTrue(packetMixin.contains("PhaseFlightMovementGuard.beginMovementPacket("));
        assertTrue(packetMixin.contains("PhaseFlightMovementGuard.endMovementPacket("));
        assertTrue(packetMixin.contains("finally"));
        assertFalse(packetMixin.contains("beginSelfMovement("));
        assertFalse(packetMixin.contains("endSelfMovement("));
    }

    @Test
    void customPayloadTeleportAuthorizationIsBoundToTheSendingPlayerOnly() throws Exception {
        String guard = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/PhaseFlightMovementGuard.java"));
        String packetMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/ServerGamePacketListenerPhaseMovementMixin.java"));
        Path oldContextMixin = Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/NetworkEventContextPhaseTeleportMixin.java");
        String payloadMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/SimpleChannelMessageBuilderPhaseTeleportMixin.java"));
        String mixinConfig = Files.readString(Path.of("src/main/resources/ae2lt.mixins.json"));

        assertTrue(guard.contains("CUSTOM_PAYLOAD_PLAYER.get() == player"));
        assertTrue(guard.contains("MAIN_THREAD_PAYLOAD_PLAYER.get() == player"));
        assertTrue(guard.contains("runAsPlayerPayloadHandler(ServerPlayer player, Runnable action)"));
        assertTrue(guard.contains("ServerPlayer previous = MAIN_THREAD_PAYLOAD_PLAYER.get()"));
        assertTrue(guard.contains("MAIN_THREAD_PAYLOAD_PLAYER.remove()"));
        assertFalse(guard.contains("PLAYER_PAYLOAD_TELEPORT_DEPTH"));
        assertFalse(guard.contains("runAsPlayerPayloadTeleport"));
        assertTrue(packetMixin.contains("@WrapMethod(method = \"handleCustomPayload\")"));
        assertTrue(packetMixin.contains("PhaseFlightMovementGuard.beginCustomPayload("));
        assertTrue(packetMixin.contains("PhaseFlightMovementGuard.endCustomPayload("));
        assertTrue(packetMixin.contains("ServerboundCustomPayloadPacket"));
        assertTrue(payloadMixin.contains("@Mixin(value = SimpleChannel.MessageBuilder.class, remap = false)"));
        assertTrue(payloadMixin.contains("method = \"lambda$consumerMainThread$1\""));
        assertTrue(payloadMixin.contains("BiConsumer;accept(Ljava/lang/Object;Ljava/lang/Object;)V"));
        assertTrue(payloadMixin.contains("ServerPlayer sender = context.getSender()"));
        assertTrue(payloadMixin.contains("runAsPlayerPayloadHandler("));
        assertTrue(payloadMixin.contains("original.call(handler, message, contextArgument)"));
        assertFalse(payloadMixin.contains("enqueueWork"));
        assertFalse(Files.exists(oldContextMixin));
        assertTrue(mixinConfig.contains("ServerGamePacketListenerPhaseMovementMixin"));
        assertTrue(mixinConfig.contains("SimpleChannelMessageBuilderPhaseTeleportMixin"));
        assertFalse(mixinConfig.contains("NetworkEventContextPhaseTeleportMixin"));
        assertFalse(mixinConfig.contains("ServerPayloadContextPhaseTeleportMixin"));
    }

    @Test
    void vanillaServerTickPositionRestoreIsNotMisreportedAsTeleport() throws Exception {
        String packetMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/ServerGamePacketListenerPhaseMovementMixin.java"));

        assertTrue(packetMixin.contains("method = \"tick\""));
        assertTrue(packetMixin.contains("ServerPlayer;absMoveTo(DDDFF)V"));
        assertTrue(packetMixin.contains("ae2lt$authorizeVanillaTickPositionRestore"));
        assertTrue(packetMixin.contains("PhaseFlightMovementGuard.runAsSelfMovement("));
    }

    @Test
    void commandQueuesBindTheirInitiatingSourceWithFinallySafeCleanup() throws Exception {
        String commandMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/CommandsPhaseTeleportMixin.java"));
        String guard = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/PhaseFlightMovementGuard.java"));
        String mixinConfig = Files.readString(Path.of("src/main/resources/ae2lt.mixins.json"));

        assertTrue(commandMixin.contains("@Mixin(Commands.class)"));
        assertTrue(commandMixin.contains("performPrefixedCommand"));
        assertTrue(commandMixin.contains("runAsCommandExecution("));
        assertTrue(commandMixin.contains("CommandSourceStack source"));
        assertTrue(guard.contains("CommandSourceStack previous = COMMAND_SOURCE.get()"));
        assertTrue(guard.contains("COMMAND_SOURCE.remove()"));
        assertTrue(mixinConfig.contains("CommandsPhaseTeleportMixin"));
    }
}
