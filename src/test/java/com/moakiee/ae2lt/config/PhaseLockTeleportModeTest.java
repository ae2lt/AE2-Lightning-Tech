package com.moakiee.ae2lt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PhaseLockTeleportModeTest {
    @Test
    void exposesTheThreeStableConfigValues() {
        assertEquals("ignore-all", PhaseLockTeleportMode.IGNORE_ALL.configValue());
        assertEquals("ignore-command", PhaseLockTeleportMode.IGNORE_COMMAND.configValue());
        assertEquals("ignore-none", PhaseLockTeleportMode.IGNORE_NONE.configValue());

        assertTrue(PhaseLockTeleportMode.isValidConfigValue("ignore-all"));
        assertTrue(PhaseLockTeleportMode.isValidConfigValue("ignore-command"));
        assertTrue(PhaseLockTeleportMode.isValidConfigValue("ignore-none"));
        assertFalse(PhaseLockTeleportMode.isValidConfigValue("all"));
    }

    @Test
    void invalidRuntimeValuesFallBackToTheManagementSafeDefault() {
        assertEquals(
                PhaseLockTeleportMode.IGNORE_COMMAND,
                PhaseLockTeleportMode.fromConfigValue("unknown"));
    }

    @Test
    void modesExposeOnlyTheirIntendedBypasses() {
        assertTrue(PhaseLockTeleportMode.IGNORE_ALL.disablesProtection());
        assertFalse(PhaseLockTeleportMode.IGNORE_ALL.ignoresPrivilegedCommands());

        assertFalse(PhaseLockTeleportMode.IGNORE_COMMAND.disablesProtection());
        assertTrue(PhaseLockTeleportMode.IGNORE_COMMAND.ignoresPrivilegedCommands());

        assertFalse(PhaseLockTeleportMode.IGNORE_NONE.disablesProtection());
        assertFalse(PhaseLockTeleportMode.IGNORE_NONE.ignoresPrivilegedCommands());
    }

    @Test
    void commonConfigDefaultsToIgnoringManagementCommands() throws Exception {
        String commonConfig = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/config/AE2LTCommonConfig.java"));

        assertTrue(commonConfig.contains("\"phaseLockTeleportMode\""));
        assertTrue(commonConfig.contains("PhaseLockTeleportMode.IGNORE_COMMAND.configValue()"));
        assertTrue(commonConfig.contains("PhaseLockTeleportMode::isValidConfigValue"));
    }
}
