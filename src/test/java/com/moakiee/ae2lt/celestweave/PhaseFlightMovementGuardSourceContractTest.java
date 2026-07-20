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
        assertTrue(guard.contains("new ServerSettings(player, blockForces, blockTeleports)"));
        assertTrue(guard.contains("SERVER_SETTINGS.remove(player.getUUID())"));
        assertTrue(guard.contains("settings.owner() == player"));
        assertTrue(guard.contains("if (player.connection == null)"));
        assertTrue(guard.contains("public static boolean isPhaseFlightActive(Player player)"));
        assertTrue(guard.contains("Map<UUID, BlockedTeleportNotice> LAST_BLOCKED_TELEPORT_NOTICE"));
        assertTrue(guard.contains("if (notice.equals(previous))"));
        assertTrue(guard.contains("LAST_BLOCKED_TELEPORT_NOTICE.remove(player.getUUID())"));
        assertTrue(guard.contains("teleport_blocked.dimension"));
        assertTrue(guard.contains("CelestweaveArmorState.isAnyClientPhaseFlightActive()"));
        assertFalse(guard.contains("PhaseFlightSubmodule.hasTransientPhaseState(player)"));
        assertFalse(guard.contains("isSubmoduleInstalled"));
        assertFalse(guard.contains("getPersistentData()"));

        String phaseFlight = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/module/PhaseFlightSubmodule.java"));
        int revoke = phaseFlight.indexOf("private static void revokePhaseFlight");
        int clearGuard = phaseFlight.indexOf("PhaseFlightMovementGuard.clear(player)", revoke);
        int escape = phaseFlight.indexOf("beginEscapePhase(player)", revoke);
        assertTrue(revoke >= 0 && clearGuard > revoke && escape > clearGuard);

        int beginEscape = phaseFlight.indexOf("private static void beginEscapePhase");
        int endEscape = phaseFlight.indexOf("private static void updateMovementGuards", beginEscape);
        String escapeBody = phaseFlight.substring(beginEscape, endEscape);
        assertFalse(escapeBody.contains("updateMovementGuards"));
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
        assertTrue(packetMixin.contains("relativeMovements.contains(RelativeMovement.X)"));
        assertTrue(packetMixin.contains("notifyBlockedTeleport(player, target)"));
        assertTrue(packetMixin.contains("!player.position().equals(target)"));
        assertTrue(dimensionMixin.contains("notifyBlockedDimensionTeleport("));
        assertTrue(dimensionMixin.contains("transition.newLevel()"));
        assertTrue(dimensionMixin.contains("transition.pos()"));
    }
}
