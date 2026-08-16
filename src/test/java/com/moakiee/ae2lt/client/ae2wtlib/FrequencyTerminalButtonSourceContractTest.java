package com.moakiee.ae2lt.client.ae2wtlib;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FrequencyTerminalButtonSourceContractTest {
    @Test
    void frequencyCardDetectionDoesNotDependOnVisibleScrollingSlots() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/ae2wtlib/FrequencyTerminalButton.java"));

        assertTrue(source.contains("screen instanceof IUniversalTerminalCapable terminalScreen"));
        assertTrue(source.contains("terminalScreen.getHost().getUpgrades()"));
        assertTrue(source.contains("upgrades.getStackInSlot(slot)"));
        assertFalse(source.contains("getSlots(SlotSemantics.UPGRADE)"));
        assertFalse(source.contains("slot.getItem()"));
    }
}
