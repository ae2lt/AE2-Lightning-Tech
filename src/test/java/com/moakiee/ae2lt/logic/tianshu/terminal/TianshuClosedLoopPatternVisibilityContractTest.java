package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TianshuClosedLoopPatternVisibilityContractTest {
    @Test
    void warehousePatternsAreOnlyExposedThroughThePatternAccessTerminal() throws Exception {
        var terminalPart = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/part/TianshuPatternEncodingTerminalPart.java"));
        var port = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/TianshuSupercomputerPortBlockEntity.java"));

        assertFalse(terminalPart.contains("TianshuPatternTerminalStorage"));
        assertFalse(terminalPart.contains("MEStorage getInventory()"));
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/tianshu/terminal/"
                        + "TianshuPatternTerminalStorage.java")));

        assertTrue(port.contains("PatternContainer"));
        assertTrue(port.contains("getTerminalPatternInventory()"));
        assertTrue(port.contains("TianshuTerminalPatternInventory"));
    }
}
