package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DeviceHubKeyMappingsSourceContractTest {
    @Test
    void forgeRuntimeHandlerHasAUniqueGeneratedListenerIdentity() throws Exception {
        String deviceHub = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/DeviceHubKeyMappings.java"));
        String frequencyCard = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/FrequencyCardKeyMappings.java"));

        assertTrue(deviceHub.contains("class DeviceHubRuntimeHandler"));
        assertFalse(deviceHub.contains("class RuntimeHandler"));
        assertTrue(frequencyCard.contains("class RuntimeHandler"));
    }

    @Test
    void forgeTickPhaseMatchesMainPostTickSemantics() throws Exception {
        String deviceHub = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/DeviceHubKeyMappings.java"));

        assertTrue(deviceHub.contains("event.phase != TickEvent.Phase.END"));
        assertTrue(deviceHub.contains("OPEN_CONFIG.consumeClick()"));
        assertTrue(deviceHub.contains("new OpenDeviceHubPacket(defaultTab)"));
    }
}
