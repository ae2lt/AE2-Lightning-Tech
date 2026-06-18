package com.moakiee.ae2lt.menu.hub;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class DeviceHubModuleConfigContractTest {

    @Test
    void hubSyncPacketCarriesSelectedModuleConfigRows() throws Exception {
        String sync = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/network/hub/DeviceHubSyncPacket.java"));

        assertTrue(sync.contains("selectedModuleIndex"));
        assertTrue(sync.contains("moduleConfigKeys"));
        assertTrue(sync.contains("moduleConfigEditable"));
    }

    @Test
    void hubActionPacketHasGenericConfigCycleAction() throws Exception {
        String action = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/network/hub/DeviceHubActionPacket.java"));

        assertTrue(action.contains("ACTION_CYCLE_MODULE_CONFIG"));
        assertTrue(action.contains("cycleSelectedModuleConfig"));
    }

    @Test
    void serverMenuKeepsSelectedModuleAlignedWithStatusSnapshot() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/hub/DeviceHubMenu.java"));

        assertTrue(menu.contains("selectedModuleIndex = status.selectedModuleIndex()"));
    }
}
