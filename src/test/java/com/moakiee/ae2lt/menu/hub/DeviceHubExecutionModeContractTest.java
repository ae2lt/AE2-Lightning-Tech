package com.moakiee.ae2lt.menu.hub;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class DeviceHubExecutionModeContractTest {
    @Test
    void hubSynchronizesAndCyclesTheThreeStateExecutionSetting() throws Exception {
        String status = source("src/main/java/com/moakiee/ae2lt/menu/hub/DeviceStatusModel.java");
        String menu = source("src/main/java/com/moakiee/ae2lt/menu/hub/DeviceHubMenu.java");
        String screen = source("src/main/java/com/moakiee/ae2lt/client/hub/DeviceHubScreen.java");
        String packet = source("src/main/java/com/moakiee/ae2lt/network/hub/DeviceHubSyncPacket.java");

        assertTrue(status.contains("settings.executionMode()"));
        assertTrue(menu.contains("s.withExecutionMode(s.executionMode().next())"));
        assertTrue(screen.contains("menu.getExecutionMode().translationKey()"));
        assertTrue(packet.contains("buf.writeEnum(executionMode)"));
        assertTrue(packet.contains("RailgunExecutionMode executionMode = buf.readEnum("));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
