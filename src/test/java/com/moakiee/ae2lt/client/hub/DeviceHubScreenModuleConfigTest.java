package com.moakiee.ae2lt.client.hub;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class DeviceHubScreenModuleConfigTest {

    @Test
    void screenRendersSelectedModuleConfigAndCyclesByClick() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/hub/DeviceHubScreen.java"));

        assertTrue(source.contains("renderModuleConfig"));
        assertTrue(source.contains("ACTION_SELECT_MODULE"));
        assertTrue(source.contains("ACTION_CYCLE_MODULE_CONFIG"));
    }

    @Test
    void languageFilesNameFlightSpeedConfig() throws Exception {
        String english = Files.readString(Path.of("src/main/resources/assets/ae2lt/lang/en_us.json"));
        String chinese = Files.readString(Path.of("src/main/resources/assets/ae2lt/lang/zh_cn.json"));

        assertTrue(english.contains("ae2lt.overload_armor.config.speed_multiplier"));
        assertTrue(english.contains("ae2lt.overload_armor.config.speed_multiplier.hint"));
        assertTrue(chinese.contains("ae2lt.overload_armor.config.speed_multiplier"));
        assertTrue(chinese.contains("ae2lt.overload_armor.config.speed_multiplier.hint"));
    }
}
