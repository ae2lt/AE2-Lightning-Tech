package com.moakiee.ae2lt.integration.ae2wtlib;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Ae2wtlibTerminalOrderSourceContractTest {
    @Test
    void tianshuTerminalRegistrationIsDeferredUntilTheItemRegistryOpens() throws Exception {
        String entrypoint = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/AE2LightningTech.java"));
        String integration = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/integration/ae2wtlib/Ae2wtlibIntegration.java"));

        assertTrue(entrypoint.contains(
                "modEventBus.addListener(Ae2wtlibIntegration::onRegister)"));
        assertFalse(entrypoint.contains("Ae2wtlibIntegration.registerTerminal();"));
        assertTrue(integration.contains("event.getRegistryKey().equals(Registries.ITEM)"));
        assertTrue(integration.contains("registerTerminal();"));
    }
}
