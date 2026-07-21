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
        assertTrue(guard.contains("withPhaseFlight(phaseModeEnabled, phaseFlying)"));
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
        String settingsPacket = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/network/FlightInertiaSyncPacket.java"));
        String draconicCompat = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/compat/DraconicChargeUpPhaseFlightMixin.java"));
        String mixinConfig = Files.readString(Path.of("src/main/resources/ae2lt.mixins.json"));

        assertTrue(state.contains("interface Access"));
        assertTrue(state.contains("public static void activate(Player player)"));
        assertTrue(state.contains("access.ae2lt$setPhaseFlying(true)"));
        assertFalse(state.contains("player.getAbilities().flying"));
        assertTrue(state.contains("abilities.mayfly = true"));
        assertTrue(state.contains("abilities.flying = access.ae2lt$isPhaseFlying()"));
        assertTrue(state.contains("rejectsExternalFlyingDisable(Abilities abilities)"));
        assertTrue(playerMixin.contains("implements PhaseFlightPlayerState.Access"));
        assertTrue(playerMixin.contains("private boolean ae2lt$phaseFlying"));
        assertTrue(playerMixin.contains("@Inject(method = \"tick\", at = @At(\"HEAD\"))"));
        assertTrue(playerMixin.contains("PhaseFlightPlayerState.syncVanillaAbilities"));
        assertTrue(phaseFlight.contains("PhaseFlightPlayerState.isFlying(player)"));
        assertTrue(clientHandler.contains("PhaseFlightPlayerState.isFlying(player)"));
        assertTrue(packetMixin.contains("PhaseFlightPlayerState.setFlying(player, packet.isFlying())"));
        assertTrue(packetMixin.contains("PhaseFlightPlayerState.syncVanillaAbilities(player)"));
        assertTrue(settingsPacket.contains("boolean phaseFlightActive"));
        assertTrue(settingsPacket.contains("boolean phaseFlying"));
        assertTrue(settingsPacket.contains("PhaseFlightPlayerState.setFlying(player, payload.phaseFlying())"));
        assertTrue(draconicCompat.contains("method = \"serverTick\""));
        assertTrue(draconicCompat.contains("method = \"clientTick\""));
        assertTrue(draconicCompat.contains("opcode = Opcodes.PUTFIELD"));
        assertTrue(draconicCompat.contains("PhaseFlightPlayerState.rejectsExternalFlyingDisable(abilities)"));
        assertTrue(mixinConfig.contains("compat.DraconicChargeUpPhaseFlightMixin"));
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
}
