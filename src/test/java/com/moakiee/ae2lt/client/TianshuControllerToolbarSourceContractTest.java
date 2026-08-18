package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TianshuControllerToolbarSourceContractTest {
    @Test
    void autoBuildAndAlgorithmSelectionRemainAvailableTogether() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuSupercomputerControllerScreen.java"));
        String packet = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/network/TianshuControllerActionPacket.java"));

        int buildButton = screen.indexOf("TextureToggleButton.ButtonType.QUICK_BUILD");
        int selectionButton = screen.indexOf("TextureToggleButton.ButtonType.CPU_SELECTION");

        assertTrue(buildButton >= 0);
        assertTrue(selectionButton > buildButton);
        assertTrue(screen.contains("TianshuControllerActionPacket.Action.AUTO_BUILD"));
        assertTrue(screen.contains("build.setPosition(x, y)"));
        assertTrue(screen.contains("selection.setPosition(x, y + 22)"));
        assertTrue(packet.contains("case AUTO_BUILD -> controller.autoBuild(player)"));
    }
}
