package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class EquipmentClientTickPhaseSourceContractTest {
    @Test
    void forgeHandlersMatchMainPostTickSemantics() throws Exception {
        assertEndPhase("FrequencyCardKeyMappings.java");
        assertEndPhase("ShieldHitFeedbackClientState.java");
    }

    private static void assertEndPhase(String fileName) throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client", fileName));

        assertTrue(source.contains("event.phase != TickEvent.Phase.END"), fileName);
    }
}
