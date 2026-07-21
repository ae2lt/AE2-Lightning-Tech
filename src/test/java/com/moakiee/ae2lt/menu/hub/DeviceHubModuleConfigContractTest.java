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

    @Test
    void railgunGlobalSettingsOwnExecutionAndChargedSplashSwitches() throws Exception {
        String status = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/hub/DeviceStatusModel.java"));
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/hub/DeviceHubMenu.java"));
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/hub/DeviceHubScreen.java"));

        assertTrue(status.contains("settings.forceOverloadRemoval()"));
        assertTrue(status.contains("settings.chargedSplash()"));
        assertTrue(menu.contains("s.withForceOverloadRemoval(!s.forceOverloadRemoval())"));
        assertTrue(menu.contains("s.withChargedSplash(!s.chargedSplash())"));
        assertTrue(screen.contains("RAILGUN_SETTING_EXECUTION_MODE"));
        assertTrue(screen.contains("RAILGUN_SETTING_CHARGED_SPLASH"));
    }

    @Test
    void railgunChainDamageIsGlobalAndGatesBothFiringPaths() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/hub/DeviceHubScreen.java"));
        String fire = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/railgun/RailgunFireService.java"));
        String beam = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/railgun/RailgunBeamService.java"));

        assertTrue(screen.contains("RAILGUN_SETTING_CHAIN_DAMAGE"));
        assertTrue(fire.contains("if (settings.chainDamage())"));
        assertTrue(beam.contains("resolveChainFromPoint("));
        assertTrue(beam.contains("if (settings.chainDamage() && player.tickCount - s.lastChainTick >= chainThrottle)"));
    }

    @Test
    void settingsReuseTheModuleListsLeftScrollbarImplementation() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/hub/DeviceHubScreen.java"));
        String configScrollbar = screen.substring(
                screen.indexOf("private void renderConfigScrollBar("),
                screen.indexOf("private void resetConfigScrollWhenSelectionChanges("));

        assertTrue(screen.contains("private static final int CONFIG_ROW_X = MODULE_LIST_X"));
        assertTrue(configScrollbar.contains("SCROLL_X"));
        assertTrue(configScrollbar.contains("private void renderScrollBar("));
    }
}
