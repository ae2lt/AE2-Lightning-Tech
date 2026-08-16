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
        assertTrue(guard.contains("CelestweaveArmorState.isAnyClientPhaseFlightActive()"));
        assertTrue(guard.contains("CelestweaveArmorState.getClientPhaseLockBlockExternalForces()"));
        assertTrue(guard.contains("StackWalker.Option.RETAIN_CLASS_REFERENCE"));
        assertTrue(guard.contains("MOVEMENT_PACKET_PLAYER.get() != player"));
        assertTrue(guard.contains("frame.getDeclaringClass() == ServerGamePacketListenerImpl.class"));
        assertTrue(guard.contains("frame.getMethodName().equals(\"handleMovePlayer\")"));
        assertTrue(guard.contains("frame.getMethodName().equals(\"handleCustomPayload\")"));
        assertTrue(guard.contains("PLAYER_PAYLOAD_TELEPORT_DEPTH"));
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
        String mixinConfig = Files.readString(Path.of("src/main/resources/ae2lt.mixins.json"));

        assertTrue(state.contains("interface Access"));
        assertTrue(state.contains("public static void activate(Player player)"));
        assertTrue(state.contains("access.ae2lt$setPhaseFlying(access.ae2lt$getVanillaFlying())"));
        assertTrue(state.contains("player.getAbilities().mayfly = true"));
        assertTrue(state.contains("access.ae2lt$setVanillaFlying(flying)"));
        assertTrue(state.contains("access.ae2lt$setVanillaFlying(access.ae2lt$isPhaseFlying())"));
        assertTrue(state.contains("access.ae2lt$isPhaseFlightLocked()"));
        assertTrue(state.contains("public static boolean readEffectiveFlying"));
        assertTrue(state.contains("public static void applyFlightInput"));
        assertTrue(state.contains("public static void synchronizeFlying"));
        assertTrue(playerMixin.contains("implements PhaseFlightPlayerState.Access"));
        assertTrue(playerMixin.contains("private boolean ae2lt$phaseFlying"));
        assertTrue(playerMixin.contains("@Inject(method = \"tick\", at = @At(\"HEAD\"))"));
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
        assertTrue(settingsPacket.contains("boolean phaseFlightActive"));
        assertTrue(settingsPacket.contains("boolean phaseFlying"));
        assertTrue(settingsPacket.contains("boolean phaseFlightLockEnabled"));
        assertTrue(settingsPacket.contains("PhaseFlightPlayerState.synchronizeFlying(player, payload.phaseFlying())"));
        assertFalse(mixinConfig.contains("DraconicChargeUpPhaseFlightMixin"));
        assertFalse(phaseFlight.contains("&& player.getAbilities().flying\n                && isPhaseModeConfigured"));
        assertTrue(phaseFlight.contains("restoreCapturedGameModeState ? wasFlying : abilities.flying"));
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
        assertTrue(entityMixin.contains("method = \"move(Lnet/minecraft/world/entity/MoverType;"));
        assertTrue(entityMixin.contains("PhaseFlightMovementGuard.blocksExternalForces(player)"));
        assertTrue(entityMixin.contains("PhaseFlightMovementGuard.isMovementPositionUpdate()"));
        assertTrue(entityMixin.contains("PhaseFlightMovementGuard.isSelfTeleportAuthorized(player)"));
        assertTrue(packetMixin.contains("relativeMovements.contains(RelativeMovement.X)"));
        assertTrue(packetMixin.contains("notifyBlockedTeleport(player, target)"));
        assertTrue(packetMixin.contains("!player.position().equals(target)"));
        assertTrue(dimensionMixin.contains("notifyBlockedDimensionTeleport("));
        assertTrue(dimensionMixin.contains("transition.newLevel()"));
        assertTrue(dimensionMixin.contains("transition.pos()"));
    }

    @Test
    void movementPacketsUseAPlayerBoundStackCheckedAuthorization() throws Exception {
        String guard = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/PhaseFlightMovementGuard.java"));
        String packetMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/ServerGamePacketListenerPhaseMovementMixin.java"));

        int identityFastReject = guard.indexOf("MOVEMENT_PACKET_PLAYER.get() != player");
        int stackWalk = guard.indexOf("MOVEMENT_PACKET_STACK_WALKER.walk");
        assertTrue(identityFastReject >= 0 && stackWalk > identityFastReject);
        assertTrue(packetMixin.contains("PhaseFlightMovementGuard.beginMovementPacket("));
        assertTrue(packetMixin.contains("PhaseFlightMovementGuard.endMovementPacket("));
        assertFalse(packetMixin.contains("beginSelfMovement("));
        assertFalse(packetMixin.contains("endSelfMovement("));
    }

    @Test
    void customPayloadTeleportAuthorizationIsBoundToTheSendingPlayerOnly() throws Exception {
        String guard = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/PhaseFlightMovementGuard.java"));
        String packetMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/ServerGamePacketListenerPhaseMovementMixin.java"));
        String payloadMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/ServerPayloadContextPhaseTeleportMixin.java"));
        String mixinConfig = Files.readString(Path.of("src/main/resources/ae2lt.mixins.json"));

        assertTrue(guard.contains("CUSTOM_PAYLOAD_PLAYER.get() != player"));
        assertTrue(guard.contains("runAsPlayerPayloadTeleport(Player player, Runnable action)"));
        assertTrue(guard.contains("runAsPlayerPayloadTeleport(Player player, Supplier<T> action)"));
        assertTrue(packetMixin.contains("PhaseFlightMovementGuard.beginCustomPayload("));
        assertTrue(packetMixin.contains("PhaseFlightMovementGuard.endCustomPayload("));
        assertTrue(payloadMixin.contains("context.listener() instanceof ServerGamePacketListenerImpl playListener"));
        assertTrue(payloadMixin.contains("ServerPlayer sender = playListener.player"));
        assertFalse(payloadMixin.contains("((ServerPayloadContext) (Object) this).player()"));
        assertTrue(payloadMixin.contains("runAsPlayerPayloadTeleport(sender, task)"));
        assertTrue(mixinConfig.contains("ServerPayloadContextPhaseTeleportMixin"));
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
        assertTrue(commandMixin.contains("executeCommandInContext"));
        assertTrue(commandMixin.contains("runAsCommandExecution("));
        assertTrue(commandMixin.contains("CommandSourceStack source"));
        assertTrue(guard.contains("CommandSourceStack previous = COMMAND_SOURCE.get()"));
        assertTrue(guard.contains("COMMAND_SOURCE.remove()"));
        assertTrue(mixinConfig.contains("CommandsPhaseTeleportMixin"));
    }
}
