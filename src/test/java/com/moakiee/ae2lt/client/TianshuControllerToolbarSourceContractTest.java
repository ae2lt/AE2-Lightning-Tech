package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TianshuControllerToolbarSourceContractTest {
    private static final Path SCREEN_SOURCE = Path.of(
            "src/main/java/com/moakiee/ae2lt/client/TianshuSupercomputerControllerScreen.java");

    @Test
    void controllerToolbarKeepsAutoBuildAndAlgorithmSelectionActions() throws Exception {
        String source = Files.readString(SCREEN_SOURCE);

        assertTrue(source.contains("TextureToggleButton.ButtonType.QUICK_BUILD"));
        assertTrue(source.contains("TianshuControllerActionPacket.Action.AUTO_BUILD"));
        assertTrue(source.contains("TextureToggleButton.ButtonType.CPU_SELECTION"));
        assertTrue(source.contains("TianshuControllerActionPacket.Action.OPEN_ALGORITHM_SELECTION"));
        assertTrue(source.contains("selection.setPosition(x, y + 22)"));
    }
}
