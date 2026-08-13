package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FrequencyScreenPacketRoutingContractTest {
    @Test
    void ae2GuiSwitchPacketUsesAe2NetworkChannel() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/gui/FrequencyScreen.java"));

        assertTrue(source.contains("NetworkHandler.instance().sendToServer("));
        assertTrue(source.contains("SwitchGuisPacket.returnToParentMenu()"));
        assertFalse(source.contains(
                "NetworkInit.sendToServer(SwitchGuisPacket.returnToParentMenu())"));
    }
}
