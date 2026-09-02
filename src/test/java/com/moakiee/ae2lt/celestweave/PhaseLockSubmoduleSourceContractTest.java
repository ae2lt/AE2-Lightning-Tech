package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class PhaseLockSubmoduleSourceContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/moakiee/ae2lt/celestweave/module/PhaseLockSubmodule.java");

    @Test
    void everyFeatureHasAnExplicitIndependentDefault() throws Exception {
        String source = Files.readString(SOURCE);

        int defaults = source.indexOf("private static boolean defaultValue(String key)");
        int nextMethod = source.indexOf("private static void", defaults);
        String defaultBody = source.substring(defaults, nextMethod).replaceAll("\\s+", " ");
        assertTrue(defaultBody.contains(
                "case ARMOR_LOCK_CONFIG_KEY, FLIGHT_LOCK_CONFIG_KEY, "
                        + "BLOCK_EXTERNAL_FORCES_CONFIG_KEY, "
                        + "BLOCK_EXTERNAL_TELEPORTS_CONFIG_KEY -> false;"));
        assertFalse(defaultBody.contains("-> true"));
        assertFalse(source.contains("!options.contains(key, Tag.TAG_BYTE) || options.getBoolean(key)"));
        assertTrue(source.contains("options.contains(key, Tag.TAG_BYTE)"));
    }

    @Test
    void configWritesAndResetsUseTheSameBooleanSemantics() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("if (value == null)"));
        assertTrue(source.contains("options.remove(key)"));
        assertTrue(source.contains("value instanceof ByteTag byteTag"));
        assertTrue(source.contains("ByteTag.valueOf(byteTag.getAsByte() != 0)"));
        assertTrue(source.contains("ByteTag.valueOf(defaultValue(key))"));
        int update = source.indexOf("private static void updateMovementProtection");
        int nextMethod = source.indexOf("private static void updateFlightLock", update);
        String updateBody = source.substring(update, nextMethod);
        assertTrue(updateBody.contains("PhaseFlightMovementGuard.updatePhaseLockProtection("));
        assertTrue(updateBody.contains("blocksExternalForces(armor)"));
        assertTrue(updateBody.contains("blocksExternalTeleports(armor)"));
    }

    @Test
    void moduleLevelActivationDoesNotTurnFeatureFlagsOn() throws Exception {
        String source = Files.readString(SOURCE);

        int activation = source.indexOf("public void onActivated");
        int deactivation = source.indexOf("public void onDeactivated", activation);
        String body = source.substring(activation, deactivation);
        assertTrue(body.contains("updateMovementProtection(player, armor)"));
        assertTrue(body.contains("updateFlightLock(player)"));
        assertFalse(body.contains("setConfig("));
        assertFalse(body.contains("ByteTag.valueOf(true)"));
    }
}
