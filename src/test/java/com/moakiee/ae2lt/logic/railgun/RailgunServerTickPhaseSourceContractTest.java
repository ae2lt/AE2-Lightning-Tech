package com.moakiee.ae2lt.logic.railgun;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RailgunServerTickPhaseSourceContractTest {
    @Test
    void forgeHandlersMatchMainPostTickSemantics() throws Exception {
        assertEndPhase("RailgunBeamService.java");
        assertEndPhase("RailgunTerrainService.java");
    }

    private static void assertEndPhase(String fileName) throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/railgun", fileName));

        assertTrue(source.contains("e.phase != TickEvent.Phase.END"), fileName);
    }
}
