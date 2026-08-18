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

        assertTrue(source.contains("slot instanceof AppEngSlot appEngSlot"));
        assertTrue(source.contains("appEngSlot.getSlotInv().getStackInSlot(0)"));
        assertFalse(source.contains("var stack = slot.getItem()"));
    }
}
