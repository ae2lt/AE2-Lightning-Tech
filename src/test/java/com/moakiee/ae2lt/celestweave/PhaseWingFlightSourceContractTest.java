package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class PhaseWingFlightSourceContractTest {
    @Test
    void phaseModuleProvidesElytraCapabilityThroughBothChestRepresentations() throws Exception {
        String moduleItem = read("src/main/java/com/moakiee/ae2lt/item/PhaseFlightSubmoduleItem.java");
        String chest = read("src/main/java/com/moakiee/ae2lt/item/CelestweaveCoreItem.java");
        String projection = read("src/main/java/com/moakiee/ae2lt/item/PhaseLockProjectionItem.java");

        assertTrue(moduleItem.contains("new DeviceCapability.ElytraFlight()"));
        assertTrue(chest.contains("canElytraFly"));
        assertTrue(chest.contains("elytraFlightTick"));
        assertTrue(projection.contains("equipmentSlot == EquipmentSlot.CHEST"));
        assertTrue(projection.contains("PhaseWingFlight.elytraFlightTick"));
    }

    @Test
    void jumpInputDrivesThrustAndCrouchWithoutPerTickPackets() throws Exception {
        String client = read("src/main/java/com/moakiee/ae2lt/client/ClientPhaseFlightHandler.java");
        String packet = read("src/main/java/com/moakiee/ae2lt/network/PhaseFlightInputPacket.java");
        String rules = read("src/main/java/com/moakiee/ae2lt/celestweave/PhaseFlightControlRules.java");
        String network = read("src/main/java/com/moakiee/ae2lt/network/NetworkInit.java");

        assertTrue(client.contains("jumpHeld == lastJumpHeld"));
        assertTrue(client.contains("PhaseFlightInputPacket.jump(jumpHeld)"));
        assertTrue(packet.contains("PhaseFlightPlayerState.setJumpHeld"));
        assertTrue(rules.contains("phaseModuleActive && jumpHeld && shiftHeld"));
        assertTrue(network.contains("PhaseFlightInputPacket.TYPE"));
    }

    @Test
    void glideParticipatesInTraversalEnergyPoseAndRendering() throws Exception {
        String module = read("src/main/java/com/moakiee/ae2lt/celestweave/module/PhaseFlightSubmodule.java");
        String wing = read("src/main/java/com/moakiee/ae2lt/celestweave/PhaseWingFlight.java");
        String energy = read("src/main/java/com/moakiee/ae2lt/celestweave/service/ArmorEnergyService.java");
        String playerMixin = read("src/main/java/com/moakiee/ae2lt/mixin/PlayerPhaseFlightMixin.java");
        String layer = read("src/main/java/com/moakiee/ae2lt/client/PhaseWingLayer.java");
        String renderers = read("src/main/java/com/moakiee/ae2lt/client/ModEntityRenderers.java");

        assertTrue(module.contains("PhaseWingFlight.isFlightActive(player)"));
        assertTrue(module.contains("player.setNoGravity(!player.isFallFlying())"));
        assertTrue(wing.contains("player.isFallFlying()"));
        assertTrue(energy.contains("!player.isFallFlying()"));
        assertTrue(playerMixin.contains("Pose.FALL_FLYING"));
        assertTrue(playerMixin.contains("Pose.STANDING"));
        assertTrue(layer.contains("extends ElytraLayer"));
        assertTrue(renderers.contains("new PhaseWingLayer"));
    }

    @Test
    void hoverAndGlideAreMutuallyExclusive() throws Exception {
        String clientMixin = read("src/main/java/com/moakiee/ae2lt/mixin/client/LocalPlayerPhaseMovementMixin.java");
        String inputPacket = read("src/main/java/com/moakiee/ae2lt/network/PhaseFlightInputPacket.java");

        assertTrue(clientMixin.contains("requestedFlying && player.isFallFlying()"));
        assertTrue(clientMixin.contains("player.stopFallFlying()"));
        assertTrue(inputPacket.contains("requestedFlying && player.isFallFlying()"));
        assertTrue(inputPacket.contains("player.stopFallFlying()"));
    }

    @Test
    void oneFlightLockControlsLandingAndExternalStateReconciliation() throws Exception {
        String module = read("src/main/java/com/moakiee/ae2lt/celestweave/module/PhaseFlightSubmodule.java");
        String state = read("src/main/java/com/moakiee/ae2lt/celestweave/PhaseFlightPlayerState.java");
        String clientMixin = read("src/main/java/com/moakiee/ae2lt/mixin/client/LocalPlayerPhaseMovementMixin.java");
        String menu = read("src/main/java/com/moakiee/ae2lt/menu/hub/DeviceHubMenu.java");
        String mixins = read("src/main/resources/ae2lt.mixins.json");

        assertTrue(module.contains("FLIGHT_LOCK_CONFIG_KEY = \"flight_lock\""));
        assertTrue(module.contains("PhaseFlightPlayerState.setFlightLocked"));
        assertTrue(state.contains("ae2lt$isPhaseFlightLocked"));
        assertTrue(state.contains("access.ae2lt$setPhaseFlying(access.ae2lt$getVanillaFlying())"));
        assertTrue(state.contains("access.ae2lt$setVanillaFlying(access.ae2lt$isPhaseFlying())"));
        assertTrue(clientMixin.contains("preserveFlightOnLanding"));
        assertTrue(clientMixin.contains("useSinglePhaseFlightInputPath"));
        assertTrue(menu.contains("PhaseFlightSubmodule.FLIGHT_LOCK_CONFIG_KEY.equals(config.key())"));
        assertFalse(mixins.contains("DraconicChargeUpPhaseFlightMixin"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
